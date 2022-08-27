package me.kaigermany.openclthreadpool.gpucontrol;

public class MinimalFS {
	private static final int ROOT_DIR_POS = 1;
	private static final int BITMAP_POS = 2;
	private static final int BITMAP_NAME = 1;
	
	public static interface VirtualIntBuffer{
		public int get(int pos);
		public boolean put(int pos, int val);
	}
	
	public static int getNextExtension_ParentFS(VirtualIntBuffer memory, int location){
		int parentClusterSize = memory.get(0);
		return memory.get(location*parentClusterSize + 1);
	}
	
	public static int Extensionbased_get_ParentFS(VirtualIntBuffer memory, int location, int pos){
		int parentClusterSize = memory.get(0);
		final int spaceSize = parentClusterSize - 2;
		int currentEntryLocation = location;
		while(pos >= spaceSize){
			currentEntryLocation = getNextExtension_ParentFS(memory, currentEntryLocation);
			if(currentEntryLocation == 0){
				return 0;
			}
			pos -= spaceSize;
		}
		return memory.get(currentEntryLocation*parentClusterSize + pos + 2);
	}
	
	public static int findDirEntry_ParentFS(VirtualIntBuffer memory, int dir_location, int entryName){
		int p = 0;
		int id;
		while((id = Extensionbased_get_ParentFS(memory, dir_location, p)) != 0){
			if(id == entryName){
				return Extensionbased_get_ParentFS(memory, dir_location, p+1);
			}
			p+=2;
		}
		return 0;
	}
	public static int RunLength_getImpl_ParentFS(VirtualIntBuffer memory, int file_location, int virualizedIndex){
		int vOffset = 0;
		int len = Extensionbased_get_ParentFS(memory, file_location, 0);
		//println("RunLengthController:get -> len="+len);
		for(int i=1; i<len*2; i+=2){
			int currRealOffset = Extensionbased_get_ParentFS(memory, file_location, i);
			int currRunlength = Extensionbased_get_ParentFS(memory, file_location, i + 1);
			//println("RunLengthController:get -> currRealOffset="+currRealOffset);
			//println("RunLengthController:get -> currRunlength="+currRunlength);
			if(virualizedIndex >= vOffset && virualizedIndex < vOffset + currRunlength){
				int localOffset = virualizedIndex - vOffset;
				//println("found: localOffset="+localOffset);
				//println("currRealOffset="+currRealOffset);
				//Thread.dumpStack();
				return currRealOffset + localOffset;
			}
			vOffset += currRunlength;
		}
		return -1;
	}
	public static boolean RunLengthInterface_put_ParentFS(VirtualIntBuffer memory, int file_location, int pos, int val) {
		int parentClusterSize = memory.get(0);
		//println("RunLengthController:getMemoryInterface$put("+pos+", "+val+")");
		int cluster = RunLength_getImpl_ParentFS(memory, file_location, pos / parentClusterSize);
		if(pos == 32) System.out.println("cluster="+cluster);
		if(cluster == -1) return false;
		memory.put(cluster*parentClusterSize + (pos % parentClusterSize), val);
		return true;
	}
	
	public static int RunLengthInterface_get_ParentFS(VirtualIntBuffer memory, int file_location, int pos) {
		int parentClusterSize = memory.get(0);
		int cluster = RunLength_getImpl_ParentFS(memory, file_location, pos / parentClusterSize);
		if(cluster == -1) return 0;
		return memory.get(cluster*parentClusterSize + (pos % parentClusterSize));
	}
	
	
	
	public static int readFromParentFS(VirtualIntBuffer memory, int threadName, int pos){
		int parentRootDirPos = memory.get(1);
		int fileEntryPointer = findDirEntry_ParentFS(memory, parentRootDirPos, threadName);
		return RunLengthInterface_get_ParentFS(memory, fileEntryPointer, pos);
	}
	
	public static boolean writeToParentFS(VirtualIntBuffer memory, int threadName, int pos, int val){
		int parentRootDirPos = memory.get(1);
		int fileEntryPointer = findDirEntry_ParentFS(memory, parentRootDirPos, threadName);
		if(pos == 32){
			System.err.println("writeToParentFS("+pos+", "+val+")");
			Thread.dumpStack();
		}
		
		return RunLengthInterface_put_ParentFS(memory, fileEntryPointer, pos, val);
	}

	
	
	public static int Entry_getObjectType(VirtualIntBuffer memory, int threadName, int entry_location){//0=undefined, 1=File, 2=Dir, 3=Extension of File or Dir-Container
		int clusterSize = readFromParentFS(memory, threadName, 0);
		return readFromParentFS(memory, threadName, entry_location*clusterSize);
	}

	public static int Entry_getNextExtension(VirtualIntBuffer memory, int threadName, int entry_location){
		int clusterSize = readFromParentFS(memory, threadName, 0);
		return readFromParentFS(memory, threadName, entry_location*clusterSize + 1);
	}
	
	public static boolean Entry_setNextExtension(VirtualIntBuffer memory, int threadName, int entry_location, int nextEntry_location) {
		int clusterSize = readFromParentFS(memory, threadName, 0);
		return writeToParentFS(memory, threadName, entry_location*clusterSize + 1, nextEntry_location);
	}
	
	public static boolean Entry_setObjectType(VirtualIntBuffer memory, int threadName, int entry_location, int typeId) {
		int clusterSize = readFromParentFS(memory, threadName, 0);
		return writeToParentFS(memory, threadName, entry_location*clusterSize, typeId);
	}

	public static int newFile(VirtualIntBuffer memory, int threadName, int uuid, int targetDirToStore){
		int cluster = allocateNewCluster(memory, threadName);
		if(cluster == -1) return -1;//out-of-memory-error
		int fileentry = cluster;
		if(!Entry_setObjectType(memory, threadName, fileentry, 1)) {//1=file, 2=dir
			BitmapHandler_setFree(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME), cluster);
			return -1;
		}
		if(!Directory_add(memory, threadName, targetDirToStore, uuid, fileentry)) {
			BitmapHandler_setFree(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME), cluster);
			return -1;
		}
		return fileentry;
	}

	public static boolean Entry_Extensionbased_put(VirtualIntBuffer memory, int threadName, int entry_location, int pos, int val) {
		int clusterSize = readFromParentFS(memory, threadName, 0);
		//System.out.println("ExtensionbasedIntBuffer:put("+pos+", "+val+")");
		final int spaceSize = clusterSize - 2;
		int currentEntry = entry_location;
		while(pos >= spaceSize){
			int nextEntry = Entry_getNextExtension(memory, threadName, currentEntry);
			if(nextEntry == 0){
				int cluster = allocateNewCluster(memory, threadName);
				if(cluster == -1) return false;
				nextEntry = cluster;
				if(!Entry_setObjectType(memory, threadName, nextEntry, 3)) return false;
				if(!Entry_setNextExtension(memory, threadName, currentEntry, nextEntry)) return false;
			}
			currentEntry = nextEntry;
			pos -= spaceSize;
		}
		return writeToParentFS(memory, threadName, currentEntry*clusterSize + pos + 2, val);
		//return true;
	}

	public static int Entry_Extensionbased_get(VirtualIntBuffer memory, int threadName, int entry_location, int pos) {
		int clusterSize = readFromParentFS(memory, threadName, 0);
		final int spaceSize = clusterSize - 2;
		int currentEntry = entry_location;
		while(pos >= spaceSize){
			currentEntry = Entry_getNextExtension(memory, threadName, currentEntry);
			if(currentEntry == 0){
				return 0;
			}
			pos -= spaceSize;
		}
		return readFromParentFS(memory, threadName, currentEntry*clusterSize + pos + 2);
	}

	public static boolean Directory_add(VirtualIntBuffer memory, int threadName, int Directory_location, int entryNameId, int entryLocation){
		int p = Directory_getSize(memory, threadName, Directory_location) * 2;
		if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p, entryNameId)) return false;
		if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p+1, entryLocation)) return false;
		return true;
	}

	public static boolean Directory_remove(VirtualIntBuffer memory, int threadName, int Directory_location, int entryNameId){
		int p = 0;
		int id;
		while((id = Entry_Extensionbased_get(memory, threadName, Directory_location, p)) != 0){
			if(id == entryNameId){
				if(Entry_Extensionbased_get(memory, threadName, Directory_location, p+2) == 0){//no following entry
					if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p, 0)) return false;
					if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p+1, 0)) return false;
				} else {
					int lastEntryPos = (Directory_getSize(memory, threadName, Directory_location)-1)*2;
					if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p, Entry_Extensionbased_get(memory, threadName, Directory_location, lastEntryPos))) return false;
					if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p+1, Entry_Extensionbased_get(memory, threadName, Directory_location, lastEntryPos+1))) return false;
					if(!Entry_Extensionbased_put(memory, threadName, Directory_location, lastEntryPos, 0)) return false;
					if(!Entry_Extensionbased_put(memory, threadName, Directory_location, lastEntryPos+1, 0)) return false;
				}
				return true;
			}
			p+=2;
		}
		return true;
	}

	public static int Directory_get(VirtualIntBuffer memory, int threadName, int Directory_location, int entryNameId){
		int p = 0;
		int id;
		while((id = Entry_Extensionbased_get(memory, threadName, Directory_location, p)) != 0){
			if(id == entryNameId){
				return Entry_Extensionbased_get(memory, threadName, Directory_location, p+1);
			}
			p+=2;
		}
		return -1;
	}
	
	public static int[] Directory_getAll(VirtualIntBuffer memory, int threadName, int Directory_location){
		int end = Directory_getSize(memory, threadName, Directory_location) * 2;
		int[] out = new int[end];
		for(int i=0; i<end; i++) out[i] = Entry_Extensionbased_get(memory, threadName, Directory_location, i);
		return out;
	}
	
	public static int Directory_getSize(VirtualIntBuffer memory, int threadName, int Directory_location){
		int p = 0;
		while(Entry_Extensionbased_get(memory, threadName, Directory_location, p) != 0){
			p+=2;
		}
		return p / 2;
	}

	public static int File_Storage_get(VirtualIntBuffer memory, int threadName, int File_location, int pos) {
		int clusterSize = readFromParentFS(memory, threadName, 0);
		int cluster = RunLength_get(memory, threadName, File_location, pos / clusterSize);
		if(cluster == -1) return 0;
		return readFromParentFS(memory, threadName, cluster*clusterSize + (pos % clusterSize));
	}
	
	private static int BitmapHandler_getNextEmptyBitPos(int bitMask){
		for(int i=0; i<32; i++) if((bitMask & (1 << i)) == 0) return i;
		return -1;
	}
	
	public static boolean BitmapHandler_initialize(VirtualIntBuffer memory, int threadName, int File_location){
		return File_Storage_put(memory, threadName, File_location, 1, 15);//mark the first 4 clusters as used.
	}

	public static int BitmapHandler_getSize(VirtualIntBuffer memory, int threadName, int File_location){
		return File_Storage_get(memory, threadName, File_location, 0);
	}

	public static boolean BitmapHandler_setSize(VirtualIntBuffer memory, int threadName, int File_location, int s){
		return File_Storage_put(memory, threadName, File_location, 0, s);
	}
	
	
	public static boolean BitmapHandler_setFree(VirtualIntBuffer memory, int threadName, int File_location, int pos){
		if(pos >= File_Storage_get(memory, threadName, File_location, 0)){
			return true;
		}
		return File_Storage_put(memory, threadName, File_location, pos / 32, File_Storage_get(memory, threadName, File_location, pos / 32) & ~(1 << (pos % 32)));
	}
	
	public static boolean BitmapHandler_setUse(VirtualIntBuffer memory, int threadName, int File_location, int pos){
		if(pos >= File_Storage_get(memory, threadName, File_location, 0)){
			return true;
		}
		int wp = (pos / 32) + 1;
		return File_Storage_put(memory, threadName, File_location, wp, File_Storage_get(memory, threadName, File_location, wp) | (1 << (pos % 32)));
	}
	
	public static int BitmapHandler_allocateNewCluster(VirtualIntBuffer memory, int threadName, int File_location) {
		int offset = 1;
		int lastMask;
		while((lastMask = File_Storage_get(memory, threadName, File_location, offset)) == -1) offset++;
		int p = BitmapHandler_getNextEmptyBitPos(lastMask);
		if(p == -1) return -1;
		offset = ((offset-1) * 32) + p;
		if(offset >= BitmapHandler_getSize(memory, threadName, File_location)) return -1;
		if(!wipeCluster(memory, threadName, offset)) return -1;//if this operation fail, it will not store this attempt to claim the cluster!
		if(!BitmapHandler_setUse(memory, threadName, File_location, offset)) return -1;
		return offset;
	}
	
	public static boolean wipeCluster(VirtualIntBuffer memory, int threadName, int offset){
		int clusterSize = readFromParentFS(memory, threadName, 0);
		offset *= clusterSize;
		for(int i=0; i<clusterSize; i++) if(!writeToParentFS(memory, threadName, i + offset, 0)) return false;
		return true;
	}

	public static boolean File_Storage_put(VirtualIntBuffer memory, int threadName, int File_location, int pos, int val) {
		int clusterSize = readFromParentFS(memory, threadName, 0);
		//println("RunLengthController:getMemoryInterface$put("+pos+", "+val+")");
		int cluster;
		while(true){
			cluster = RunLength_get(memory, threadName, File_location, pos / clusterSize);
			//println("cluster="+cluster);
			if(cluster != -1) break;
			//println("RunLengthController:getMemoryInterface$put.allocateNewCluster::begin");
			int loc = allocateNewCluster(memory, threadName);
			if(loc == -1) return false;
			//println("RunLengthController:getMemoryInterface$put.allocateNewCluster -> " + loc);
			//Thread.dumpStack();
			if(!RunLength_append(memory, threadName, File_location, loc)) return false;
		}
		//println("RunLengthController:getMemoryInterface$put at " + (cluster*clusterSize + (pos % clusterSize)));
		//println("RunLengthController:getMemoryInterface$put at cluster " + cluster + ", field " + (pos % clusterSize));
		return writeToParentFS(memory, threadName, cluster*clusterSize + (pos % clusterSize), val);
		//return true;
	}
	
	public static int RunLength_get(VirtualIntBuffer memory, int threadName, int File_location, int virualizedIndex){
		int vOffset = 0;
		int len = RunLength_getSize(memory, threadName, File_location);
		//println("RunLengthController:get -> len="+len);
		for(int i=1; i<len*2; i+=2){
			int currRealOffset = Entry_Extensionbased_get(memory, threadName, File_location, i);
			int currRunlength = Entry_Extensionbased_get(memory, threadName, File_location, i + 1);
			//println("RunLengthController:get -> currRealOffset="+currRealOffset);
			//println("RunLengthController:get -> currRunlength="+currRunlength);
			if(virualizedIndex >= vOffset && virualizedIndex < vOffset + currRunlength){
				int localOffset = virualizedIndex - vOffset;
				//println("found: localOffset="+localOffset);
				//println("currRealOffset="+currRealOffset);
				//Thread.dumpStack();
				return currRealOffset + localOffset;
			}
			vOffset += currRunlength;
		}
		return -1;
	}
	
	public static boolean RunLength_append(VirtualIntBuffer memory, int threadName, int File_location, int realIndex){
		//println("append("+realIndex+").pre: " + Arrays.toString(getAll()));
		int lastEntryPos = (RunLength_getSize(memory, threadName, File_location)-1)*2;
		//println("append: lastEntryPos="+lastEntryPos);
		int lastRunLen;
		if(lastEntryPos >= 0 && Entry_Extensionbased_get(memory, threadName, File_location, lastEntryPos+1) + (lastRunLen = Entry_Extensionbased_get(memory, threadName, File_location, lastEntryPos+2)) == realIndex){//matches last run-frame
			if(!Entry_Extensionbased_put(memory, threadName, File_location, lastEntryPos+2, lastRunLen + 1)) return false;//incr run-length
		} else {
			if(!Entry_Extensionbased_put(memory, threadName, File_location, 0, Entry_Extensionbased_get(memory, threadName, File_location, 0) + 1)) return false;//incr index count
			if(!Entry_Extensionbased_put(memory, threadName, File_location, lastEntryPos+3, realIndex)) return false;//add new run-frame
			if(!Entry_Extensionbased_put(memory, threadName, File_location, lastEntryPos+4, 1)) return false;//at requested offset and length=1;
		}
		//println("append().post: " + Arrays.toString(getAll()));
		return true;
	}
	
	public static int RunLength_getSize(VirtualIntBuffer memory, int threadName, int File_location){
		return Entry_Extensionbased_get(memory, threadName, File_location, 0);
	}
	
	public static int[] RunLength_getAll(VirtualIntBuffer memory, int threadName, int File_location){
		int[] out = new int[RunLength_getSize(memory, threadName, File_location) * 2];
		//println(""+out.length);
		for(int i=0; i<out.length; i++) out[i] = Entry_Extensionbased_get(memory, threadName, File_location, i + 1);
		return out;
	}
	
	public static boolean RunLength_writeRawInitaialData(VirtualIntBuffer memory, int threadName, int File_location, int[] data){
		for(int i=0; i<data.length; i++){
			if(!Entry_Extensionbased_put(memory, threadName, File_location, i, data[i])) return false;
		}
		return true;
	}
	
	public static int allocateNewCluster(VirtualIntBuffer memory, int threadName){
		return BitmapHandler_allocateNewCluster(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME));
	}
	
	public static boolean deallocateNewCluster(VirtualIntBuffer memory, int threadName, int clusterId){
		return BitmapHandler_setFree(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME), clusterId);
	}
	
	public static boolean overrideBitmapSize(VirtualIntBuffer memory, int threadName, int newLen){
		return BitmapHandler_setSize(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME), newLen);
	}
	
	public static boolean init_makeGenericEntry(VirtualIntBuffer memory, int threadName, int clusterSize, int clusterIndex, int entryType, boolean wipeSpace) {
		if(!writeToParentFS(memory, threadName, clusterSize*clusterIndex, entryType)) return false;
		if(!writeToParentFS(memory, threadName, clusterSize*clusterIndex +1, 0)) return false;
		if(wipeSpace){
			for(int i=2; i<clusterSize; i++) if(!writeToParentFS(memory, threadName, clusterSize*clusterIndex +i, 0)) return false;
		}
		return true;
	}
	
	public static boolean initSubFS(VirtualIntBuffer memory, int threadName, int clusterSize, int usableSpace){
		int clusterCount = usableSpace / clusterSize;
		memory.put(0, clusterSize);
		memory.put(1, ROOT_DIR_POS);
		if(!init_makeGenericEntry(memory, threadName, clusterSize, ROOT_DIR_POS, 2, true)) return false;//mft-root
		if(!init_makeGenericEntry(memory, threadName, clusterSize, BITMAP_POS, 1, true)) return false;//bitmap-entry
		if(!Directory_add(memory, threadName, ROOT_DIR_POS, BITMAP_NAME, BITMAP_POS)) return false;//interface mft-root(location=1), add bitmap-entry(will be located at pos 2)
		//write bitmap-file
		//VirtualIntBuffer ib = new File(BITMAP_POS).getMemoryInterface();
		//BitmapHandler b = new BitmapHandler(BITMAP_POS);
		//b.rlc.writeRawInitaialData(new int[]{1, 3, 1});
		//VirtualIntBuffer vib = b.getExtensionbasedIntBufferInterface();
		if(!Entry_Extensionbased_put(memory, threadName, BITMAP_POS, 0, 1)) return false;
		if(!Entry_Extensionbased_put(memory, threadName, BITMAP_POS, 1, 3)) return false;
		if(!Entry_Extensionbased_put(memory, threadName, BITMAP_POS, 2, 1)) return false;
		if(!BitmapHandler_setSize(memory, threadName, BITMAP_POS, clusterCount)) return false;
		return BitmapHandler_initialize(memory, threadName, BITMAP_POS);
		//return true;
	}
	/*
	private final int ROOT_DIR_POS = 1;
	private final int BITMAP_POS = 2;
	private final int BITMAP_NAME = 1;
	private VirtualIntBuffer memory;
	private int clustorSize;
	//private int bitmap_offset;
	
	public MinimalFS(VirtualIntBuffer memory, int clustorSize, int usableSpace){
		this.memory = memory;
		this.clustorSize = clustorSize;
		int clustorCount = usableSpace / clustorSize;
		memory.put(0, clustorSize);
		memory.put(1, ROOT_DIR_POS);
		makeGenericEntry(ROOT_DIR_POS, 2, true);//mft-root
		makeGenericEntry(BITMAP_POS, 1, true);//bitmap-entry
		new Directory(ROOT_DIR_POS).add(BITMAP_NAME, BITMAP_POS);//interface mft-root(location=1), add bitmap-entry(will be located at pos 2)
		//write bitmap-file
		//VirtualIntBuffer ib = new File(BITMAP_POS).getMemoryInterface();
		BitmapHandler b = new BitmapHandler(BITMAP_POS);
		//b.rlc.writeRawInitaialData(new int[]{1, 3, 1});
		//VirtualIntBuffer vib = b.getExtensionbasedIntBufferInterface();
		b.Extensionbased_put(0, 1);
		b.Extensionbased_put(1, 3);
		b.Extensionbased_put(2, 1);
		b.setSize(clustorCount);
		b.initialize();
	}
	
	public MinimalFS(VirtualIntBuffer memory){
		this.memory = memory;
		clustorSize = memory.get(0);
		//ROOT_DIR_POS = memory.get(1);
	}
	
	public File newFile(int uuid, int targetDirToStore){
		Clustor c = allocateNewClustor();
		if(c == null) return null;//out-of-memory-error
		Entry e = c.getAsEntry();
		e.setObjectType(1);//1=file, 2=dir
		int fileLocation = e.location;
		File f = new File(fileLocation);
		new Directory(targetDirToStore).add(uuid, fileLocation);
		return f;
	}
	
	public static VirtualIntBuffer makeSimpeBufferInterface(final IntBuffer ib){
		return new VirtualIntBuffer() {
			@Override
			public boolean put(int pos, int val) {
				try{
					ib.put(pos, val);
					return true;
				}catch(Exception e){
					return false;
				}
			}
			
			@Override
			public int get(int pos) {
				return ib.get(pos);
			}
		};
	}
	
	public static interface VirtualIntBuffer{
		public int get(int pos);
		public boolean put(int pos, int val);
	}

	public void makeGenericEntry(int clustorIndex, int entryType, boolean wipeSpace) {
		memory.put(clustorSize*clustorIndex, entryType);
		memory.put(clustorSize*clustorIndex +1, 0);
		if(wipeSpace){
			for(int i=2; i<clustorSize; i++) memory.put(clustorSize*clustorIndex +i, 0);
		}
	}
	
	public int allocateNewClustorRAW(){
		int pos = new BitmapHandler(new Directory(ROOT_DIR_POS).get(BITMAP_NAME)).allocateNewClustor();
		return pos;
	}
	
	public Clustor allocateNewClustor(){
		//println("allocateNewClustor() -> bitmap-size: " + new BitmapHandler(new Directory(1).get(1)).getSize());
		int pos = allocateNewClustorRAW();
		//println("allocateNewClustor() pos="+pos);
		if(pos == -1) {
			//println("allocateNewClustor: out-of-space-exception!");
			return null;
		}
		//println("allocateNewClustor() -> allocated pos: " + pos);
		return new Clustor(pos);
	}
	
	public void deallocateNewClustor(int clustorId){
		new BitmapHandler(new Directory(ROOT_DIR_POS).get(BITMAP_NAME)).setFree(clustorId);
	}
	
	public void overrideBitmapSize(int newLen){
		new BitmapHandler(new Directory(ROOT_DIR_POS).get(BITMAP_NAME)).setSize(newLen);
	}
	
	public class Clustor{
		public int location;
		public Clustor(int location){
			this.location = location;
		}
		public Entry getAsEntry(){
			return new Entry(location);
		}
	}
	public class Entry extends Clustor{//dataTye: [0], nextExtension: [1], free-to-use-space: [2..clustorSize]
		
		public Entry(int location){
			super(location);
		}
		public int getObjectType(){//0=undefined, 1=File, 2=Dir, 3=Extension of File or Dir-Container
			return memory.get(location);
		}
		
		public Directory getAsDir(){
			return new Directory(location);
		}
		
		public File getAsFile(){
			return new File(location);
		}
		
		public Entry getNextExtension(){
			int nextPos = memory.get(location*clustorSize + 1);
			if(nextPos == 0) return null;
			return new Entry(nextPos);
		}
		
		public void setNextExtension(Entry nextEntry) {
			memory.put(location*clustorSize + 1, nextEntry.location);
		}
		public void setObjectType(int typeId) {
			memory.put(location*clustorSize, typeId);
		}
		
		public boolean Extensionbased_put(int pos, int val) {
			//System.out.println("ExtensionbasedIntBuffer:put("+pos+", "+val+")");
			final int spaceSize = clustorSize - 2;
			Entry currentEntry = Entry.this;
			while(pos >= spaceSize){
				Entry nextEntry = currentEntry.getNextExtension();
				if(nextEntry == null){
					Clustor c = allocateNewClustor();
					if(c == null) return false;
					nextEntry = c.getAsEntry();
					nextEntry.setObjectType(3);
					currentEntry.setNextExtension(nextEntry);
				}
				currentEntry = nextEntry;
				pos -= spaceSize;
				//allocateNewClustor
			}
			memory.put(currentEntry.location*clustorSize + pos + 2, val);
			return true;
		}
		
		public int Extensionbased_get(int pos) {
			//System.out.println("ExtensionbasedIntBuffer:get("+pos+")");
			final int spaceSize = clustorSize - 2;
			Entry currentEntry = Entry.this;
			while(pos >= spaceSize){
				currentEntry = currentEntry.getNextExtension();
				if(currentEntry == null){
					return 0;
				}
				pos -= spaceSize;
			}
			return memory.get(currentEntry.location*clustorSize + pos + 2);
		}
	}
	
	public class Directory extends Entry{
		public Directory(int location){
			super(location);
		}
		public void add(int entryNameId, int entryLocation){
			int p = getSize() * 2;
			//VirtualIntBuffer ib = getExtensionbasedIntBufferInterface();
			this.Extensionbased_put(p, entryNameId);
			this.Extensionbased_put(p+1, entryLocation);
		}
		public void remove(int entryNameId){
			//VirtualIntBuffer ib = getExtensionbasedIntBufferInterface();
			int p = 0;
			int id;
			while((id = this.Extensionbased_get(p)) != 0){
				if(id == entryNameId){
					if(this.Extensionbased_get(p+2) == 0){//no following entry
						this.Extensionbased_put(p, 0);
						this.Extensionbased_put(p+1, 0);
					} else {
						int lastEntryPos = (getSize()-1)*2;
						this.Extensionbased_put(p, this.Extensionbased_get(lastEntryPos));
						this.Extensionbased_put(p+1, this.Extensionbased_get(lastEntryPos+1));
						this.Extensionbased_put(lastEntryPos, 0);
						this.Extensionbased_put(lastEntryPos+1, 0);
					}
					return;
				}
				p+=2;
			}
		}
		public int get(int entryNameId){
			//VirtualIntBuffer ib = getExtensionbasedIntBufferInterface();
			int p = 0;
			int id;
			while((id = this.Extensionbased_get(p)) != 0){
				if(id == entryNameId){
					return this.Extensionbased_get(p+1);
				}
				p+=2;
			}
			return -1;
		}
		public int[] getAll(){
			//VirtualIntBuffer ib = getExtensionbasedIntBufferInterface();
			int end = getSize() * 2;
			int[] out = new int[end];
			for(int i=0; i<end; i++) out[i] = this.Extensionbased_get(i);
			return out;
		}
		public int getSize(){
			//VirtualIntBuffer ib = getExtensionbasedIntBufferInterface();
			int p = 0;
			while(this.Extensionbased_get(p) != 0){
				p+=2;
			}
			return p / 2;
		}
	}
	
	public class File extends Entry{
		public RunLengthController rlc;
		public File(int location) {
			super(location);
			rlc = new RunLengthController( this);
		}

		public VirtualIntBuffer getMemoryInterface() {
			return rlc.getMemoryInterface();
		}
		
	}
	
	public class RunLengthController{
		//private VirtualIntBuffer virtual_disk;
		private File file;
		public RunLengthController(File file){
			//virtual_disk = virtualIntBuffer;
			this.file = file;
		}
		
		public VirtualIntBuffer getMemoryInterface() {
			return new VirtualIntBuffer() {
				@Override
				public boolean put(int pos, int val) {
					//println("RunLengthController:getMemoryInterface$put("+pos+", "+val+")");
					int clustor;
					while(true){
						clustor = RunLengthController.this.get(pos / clustorSize);
						//println("clustor="+clustor);
						if(clustor != -1) break;
						//println("RunLengthController:getMemoryInterface$put.allocateNewClustor::begin");
						int loc = MinimalFS.this.allocateNewClustorRAW();
						if(loc == -1) return false;
						//println("RunLengthController:getMemoryInterface$put.allocateNewClustor -> " + loc);
						//Thread.dumpStack();
						RunLengthController.this.append(loc);
					}
					//println("RunLengthController:getMemoryInterface$put at " + (clustor*clustorSize + (pos % clustorSize)));
					//println("RunLengthController:getMemoryInterface$put at clustor " + clustor + ", field " + (pos % clustorSize));
					memory.put(clustor*clustorSize + (pos % clustorSize), val);
					return true;
				}
				@Override
				public int get(int pos) {
					//println("RunLengthController:getMemoryInterface$get raw at virtual clustor " + (pos / clustorSize) + ", field " + (pos % clustorSize));
					int clustor = RunLengthController.this.get(pos / clustorSize);
					if(clustor == -1) return 0;
					return memory.get(clustor*clustorSize + (pos % clustorSize));
				}
			};
		}
		
		public int get(int virualizedIndex){
			int vOffset = 0;
			int len = getSize();
			//println("RunLengthController:get -> len="+len);
			for(int i=1; i<len*2; i+=2){
				int currRealOffset = file.Extensionbased_get(i);
				int currRunlength = file.Extensionbased_get(i + 1);
				//println("RunLengthController:get -> currRealOffset="+currRealOffset);
				//println("RunLengthController:get -> currRunlength="+currRunlength);
				if(virualizedIndex >= vOffset && virualizedIndex < vOffset + currRunlength){
					int localOffset = virualizedIndex - vOffset;
					//println("found: localOffset="+localOffset);
					//println("currRealOffset="+currRealOffset);
					//Thread.dumpStack();
					return currRealOffset + localOffset;
				}
				vOffset += currRunlength;
			}
			return -1;
		}
		
		public void append(int realIndex){
			//println("append("+realIndex+").pre: " + Arrays.toString(getAll()));
			int lastEntryPos = (getSize()-1)*2;
			//println("append: lastEntryPos="+lastEntryPos);
			int lastRunLen;
			if(lastEntryPos >= 0 && file.Extensionbased_get(lastEntryPos+1) + (lastRunLen = file.Extensionbased_get(lastEntryPos+2)) == realIndex){//matches last run-frame
				file.Extensionbased_put(lastEntryPos+2, lastRunLen + 1);//incr run-length
			} else {
				file.Extensionbased_put(0, file.Extensionbased_get(0) + 1);//incr index count
				file.Extensionbased_put(lastEntryPos+3, realIndex);//add new run-frame
				file.Extensionbased_put(lastEntryPos+4, 1);//at requested offset and length=1;
			}
			//println("append().post: " + Arrays.toString(getAll()));
		}
		public int getSize(){
			return file.Extensionbased_get(0);
		}
		public int[] getAll(){
			int[] out = new int[getSize() * 2];
			//println(""+out.length);
			for(int i=0; i<out.length; i++) out[i] = file.Extensionbased_get(i + 1);
			return out;
		}
		public void writeRawInitaialData(int[] data){
			for(int i=0; i<data.length; i++){
				file.Extensionbased_put(i, data[i]);
			}
		}
	}
	
	public class BitmapHandler extends File{

		public BitmapHandler(int location) {
			super(location);
		}
		
		public void initialize(){
			getMemoryInterface().put(1, 15);//mark the first 4 clustors as used.
		}

		public int getSize(){
			return getMemoryInterface().get(0);
		}

		public void setSize(int s){
			getMemoryInterface().put(0, s);
		}
		
		
		public void setFree(int pos){
			VirtualIntBuffer ib = getMemoryInterface();
			if(pos >= ib.get(0)){
				return;
			}
			ib.put(pos / 32, ib.get(pos / 32) & ~(1 << (pos % 32)));
		}
		public void setUse(int pos){
			VirtualIntBuffer ib = getMemoryInterface();
			if(pos >= ib.get(0)){
				return;
			}
			int wp = (pos / 32) + 1;
			ib.put(wp, ib.get(wp) | (1 << (pos % 32)));
		}
		
		public int allocateNewClustor() {
			VirtualIntBuffer ib = getMemoryInterface();
			int offset = 1;
			int lastMask;
			while((lastMask = ib.get(offset)) == -1) offset++;
			//println("lastMask->"+lastMask);
			int p = getNextEmptyBitPos(lastMask);
			//println("getNextEmptyBitPos()->"+p);
			if(p == -1) return -1;
			offset = ((offset-1) * 32) + p;
			if(offset >= getSize()) return -1;
			//println("allocateNewClustor() -> offset="+offset);
			setUse(offset);
			return offset;
		}
		
		private int getNextEmptyBitPos(int bitMask){
			for(int i=0; i<32; i++) if((bitMask & (1 << i)) == 0) return i;
			return -1;
		}
	}
	*/
}
