package me.kaigermany.openclthreadpool.core;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import me.kaigermany.openclthreadpool.core.MemoryModel.MinimalFS.File;

public class MemoryModel {
	public static Clustor getClustor(IntBuffer memory, IntBuffer configBuffer, int index) {
		int clustorSize = configBuffer.get(1);
		return new Clustor(memory, clustorSize, index);
	}

	public static class Clustor {
		private IntBuffer memory;
		private int clustorSize;
		private int offset;

		public Clustor(IntBuffer memory, int clustorSize, int index) {
			this.memory = memory;
			this.clustorSize = clustorSize;
			this.offset = index * clustorSize;
		}

		public int getLen() {
			return clustorSize;
		}

		public int get(int pos) {
			return get0(pos);
		}

		protected int get0(int pos) {
			return memory.get(offset + pos);
		}

		public void set(int pos, int val) {
			set0(pos, val);
		}

		public void set0(int pos, int val) {
			memory.put(offset + pos, val);
		}

		public IntBuffer getMemory() {
			return memory;
		}
		
		public int getIndexInMemory(){
			return offset / clustorSize;
		}
	}

	public static class ClassInterface extends Clustor {
		private static final int CLASS_UUID_INDEX = 0;
		private static final int POINTERCOUNT_INDEX = 1;
		private static final int NEXT_CLUSTOR_INDEX = 2;
		private static final int REF_FIELD_COUNT_INDEX = 3;
		private static final int REF_ARRAY_OFFSET_INDEX = 4;

		// [0]: class-uuid
		// [1]: pointer-Count
		// [2]: next-cluster-pointer (oder 0)
		// [3]: refField-Count
		// [4..([2]+4)] refs
		public ClassInterface(IntBuffer memory, int clustorSize, int index) {
			super(memory, clustorSize, index);
		}

		@Override
		public int get(int pos) {//todo updaten, len des nächsten elements wird nicht mehr passen
			ClassInterface curentClustor = this;
			while (pos > curentClustor.getLen()) {
				pos -= curentClustor.getLen();
				curentClustor = new ClassInterface(super.getMemory(), curentClustor.getLen(),
						curentClustor.get0(NEXT_CLUSTOR_INDEX));
			}
			return get0(pos);
		}

		@Override
		public void set(int pos, int val) {
			ClassInterface curentClustor = this;
			while (pos > curentClustor.getLen()) {
				pos -= curentClustor.getLen();
				curentClustor = new ClassInterface(super.getMemory(), curentClustor.getLen(),
						curentClustor.get0(NEXT_CLUSTOR_INDEX));
			}
			set0(pos, val);
		}
		
		public int getRefCount() {
			return get0(REF_FIELD_COUNT_INDEX);
		}

		public void setRefCount(int len) {
			set0(REF_FIELD_COUNT_INDEX, len);
		}

		public int getRef(int pos) {
			return get(REF_ARRAY_OFFSET_INDEX + pos);
		}

		public void setRef(int pos, int pointer) {
			set(REF_ARRAY_OFFSET_INDEX + pos, pointer);
		}
		/*
		public static ClassInterface newInstance(IntBuffer memory, int threadId, int clustorSize, int clusterCount,
				int class_uuid, int refFieldCount) {
			return newInstance0(memory, threadId, clustorSize, clusterCount, class_uuid, refFieldCount);
		}

		public static ClassInterface newInstance0(IntBuffer memory, int threadId, int clustorSize, int clusterCount,
				int class_uuid, int refFieldCount) {
			List threadList = new List(memory, clustorSize, 0);

		}
	*/	
		public ClassExtension getNextExtension(){
			int nextIndex = get0(NEXT_CLUSTOR_INDEX);
			if(nextIndex == 0) return null;
			return new ClassExtension(getMemory(), getLen(), nextIndex);
		}
		
		
		
		public static class ClassExtension extends Clustor {
			public ClassExtension(IntBuffer memory, int clustorSize, int index) {
				super(memory, clustorSize, index);
			}
			
			public int getNextExtensionPointer(){
				return get0(0);
			}
			
			public void setNextExtensionPointer(int ptr){
				set0(0, ptr);
			}
			
			public ClassExtension getNextExtension(){
				int nextIndex = getNextExtensionPointer();
				if(nextIndex == 0) return null;
				return new ClassExtension(getMemory(), getLen(), nextIndex);
			}
		}
	}

	public static class List extends ClassInterface {
		private static final int CLASS_TYPE_UUID = 2;
		public List(IntBuffer memory, int clustorSize, int index) {
			super(memory, clustorSize, index);
		}

		public void add(int pointer) {
			add(size(), pointer);
		}

		public void add(int pos, int pointer) {
			int size = getRefCount();
			setRefCount(size + 1);
			ensureSpace(size + 1);
			if(pos < size){
				for(int i=pos; i<size; i++){
					int oldPointer = getRef(i);
					setRef(i, pointer);
					pointer = oldPointer;
				}
			}
			setRef(size, pointer);
		}

		public void remove(int pos) {

		}

		public int size() {
			return getRefCount();
		}
		/*
		public static List newInstance(IntBuffer memory, int threadId, int clustorSize) {
			int refFieldCount = 0;
			ClassInterface ref = newInstance0(memory, threadId, clustorSize, 1,	CLASS_TYPE_UUID, refFieldCount);
			return new List(memory, clustorSize, ref.getIndexInMemory());
		}
		*/
		private void ensureSpace(int newRefCount){
			newRefCount += 4;
			if(newRefCount < getLen()) return;
			ClassExtension nextClustor = getNextExtension();
			newRefCount -= getLen();
			while(newRefCount > 0){
				nextClustor = nextClustor.getNextExtension();
				newRefCount -= getLen();
			}
		}
	}
	
	
	
	
	
	
	
	public static void MinimalFS_reimplementation_unittest(){
		final int frameSize = 16;
		final int rows = 16;
		final int frameSize2 = 8;
		final int rows2 = 10;
		IntBuffer ib = IntBuffer.allocate(frameSize*rows);
		printBuffer(ib);
		MemoryModel.MinimalFS fs = new MemoryModel.MinimalFS(new MemoryModel.MinimalFS.VirtualIntBuffer() {
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
		}, frameSize, frameSize*rows);
		printBuffer(ib);
		int demoThreadName = 12345;
		MinimalFS.File f = fs.newFile(demoThreadName, fs.ROOT_DIR_POS);
		printBuffer(ib);
		fs = new MemoryModel.MinimalFS(f.getMemoryInterface(), frameSize2, frameSize2*rows2);
		printBuffer(ib);
		
		me.kaigermany.openclthreadpool.gpucontrol.MinimalFS.VirtualIntBuffer vib = new me.kaigermany.openclthreadpool.gpucontrol.MinimalFS.VirtualIntBuffer() {
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
		int root_dir_ptr = 1;
		for(int i=0; i<3; i++){
			int filePtr = me.kaigermany.openclthreadpool.gpucontrol.MinimalFS.newFile(vib, demoThreadName, 6789, root_dir_ptr);
			printBuffer(ib);
			System.out.println("filePtr="+filePtr);
			if(filePtr != -1) break;
			if(!fs.expandFile(f)){
				System.out.println("ERROR while alloc new file chunk");
			}
		}
	}
	
	
	
	
	public static void MinimalFS_unittest(){
		if(true){
			MinimalFS_reimplementation_unittest();
			return;
		}
		final int frameSize = 8;
		final int rows = 10;
		IntBuffer ib = IntBuffer.allocate(frameSize*rows);
		printBuffer(ib);
		MinimalFS fs = new MinimalFS(new MinimalFS.VirtualIntBuffer() {
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
		}, frameSize, frameSize*rows);
		printBuffer(ib);
		/*
[8, 1, 0, 0, 0, 0, 0, 0]
[2, 0, 1, 2, 0, 0, 0, 0]
[1, 0, 1, 3, 1, 0, 0, 0]
[8, 15, 0, 0, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
		 */
		MinimalFS.File f = fs.newFile(12345, fs.ROOT_DIR_POS);
		printBuffer(ib);
		/*
[8, 1, 0, 0, 0, 0, 0, 0]
[2, 0, 1, 2, 12345, 4, 0, 0]
[1, 0, 1, 3, 1, 0, 0, 0]
[8, 31, 0, 0, 0, 0, 0, 0]
[1, 0, 0, 0, 0, 0, 0, 0]   // <- f.location=4 | file '12345' (run-length-stream-offset)
[0, 0, 0, 0, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
		 */
		System.out.println("f.location="+f.location);
		// f.location=4
		for(int i=0; i<15; i++) System.out.println();
		
		
		MinimalFS.VirtualIntBuffer vib_filebuffer = f.getMemoryInterface();
		for(int i=0; i<12; i++) vib_filebuffer.put(i, 500+i);
		printBuffer(ib);
		/*
[8, 1, 0, 0, 0, 0, 0, 0]
[2, 0, 1, 2, 12345, 4, 0, 0]
[1, 0, 1, 3, 1, 0, 0, 0]
[8, 127, 0, 0, 0, 0, 0, 0]
[1, 0, 2, 5, 1, 6, 1, 0]
[500, 501, 502, 503, 504, 505, 506, 507]
[508, 509, 510, 511, 0, 0, 0, 0]
[0, 0, 0, 0, 0, 0, 0, 0]
		 */
		for(int i=12; i<12+8; i++) vib_filebuffer.put(i, 500+i);
		//vib_filebuffer.put(1, 210);
		printBuffer(ib);
		for(int i=12+8; i<12+16; i++) vib_filebuffer.put(i, 500+i);
		//vib_filebuffer.put(1, 210);
		printBuffer(ib);
		for(int i=12+16; i<12+28; i++) vib_filebuffer.put(i, 500+i);
		printBuffer(ib);
		System.out.println(vib_filebuffer.put(12+28, 500+12+28));
		printBuffer(ib);
	}
	
	public static void printBuffer(IntBuffer ib){
		final int w = 8;
		ArrayList<Integer> list = new ArrayList<Integer>(w);
		for(int i=0; i<ib.capacity(); i++) {
			list.add(ib.get(i));
			if(i % w == w-1) {
				System.out.println(list);
				list.clear();
			}
		}
		System.out.println();
	}
	
	public static void println(String text){
		int stackDepth = Thread.currentThread().getStackTrace().length;
		stackDepth -= 5;
		for(int i=0; i<stackDepth; i++) System.out.print('\t');
		System.out.println(text);
	}
	
	
	
	
	public static class MinimalFS{
		public final int ROOT_DIR_POS = 1;
		private final int BITMAP_POS = 2;
		public final int BITMAP_NAME = 1;
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
		
		public boolean expandFile(File f) {//append a new clamed clustor to the file allocation table
			Entry currentEntry = f;
			Entry lastEntry = f;
			while((currentEntry = (lastEntry = currentEntry).getNextExtension()) != null);
			Clustor c = allocateNewClustor();
			if(c == null) return false;
			currentEntry = c.getAsEntry();
			currentEntry.setObjectType(3);
			lastEntry.setNextExtension(currentEntry);
			return true;
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

		public void makeGenericEntry(/*VirtualIntBuffer memory, int clustorSize, */int clustorIndex, int entryType, boolean wipeSpace) {
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
		
		public BitmapHandler getBitmap(){
			return new BitmapHandler(new Directory(ROOT_DIR_POS).get(BITMAP_NAME));
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
			/*
			public VirtualIntBuffer getExtensionbasedIntBufferInterface(){
					return new VirtualIntBuffer() {
					@Override
					public void put(int pos, int val) {
						System.out.println("ExtensionbasedIntBuffer:put("+pos+", "+val+")");
						final int spaceSize = clustorSize - 2;
						Entry currentEntry = Entry.this;
						while(pos >= spaceSize){
							Entry nextEntry = currentEntry.getNextExtension();
							if(nextEntry == null){
								nextEntry = allocateNewClustor().getAsEntry();
								nextEntry.setObjectType(3);
								currentEntry.setNextExtension(nextEntry);
							}
							currentEntry = nextEntry;
							pos -= spaceSize;
							//allocateNewClustor
						}
						memory.put(currentEntry.location*clustorSize + pos + 2, val);
					}
					@Override
					public int get(int pos) {
						System.out.println("ExtensionbasedIntBuffer:get("+pos+")");
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
				};
			}
			*/
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
				println("dir add: entryNameId="+entryNameId+", entryLocation="+entryLocation+", my loc="+location);
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
				rlc = new RunLengthController(/*getExtensionbasedIntBufferInterface()*/ this);
			}

			public VirtualIntBuffer getMemoryInterface() {
				return rlc.getMemoryInterface();
			}
			
		}
		
		public class RunLengthController{
			//private VirtualIntBuffer virtual_disk;
			private File file;
			public RunLengthController(/*VirtualIntBuffer virtualIntBuffer*/ File file){
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
				growReservedToFullSize(s);
			}
			
			public void growReservedToFullSize(int size){
				setUse(size-1);
				setFree(size-1);
			}
			
			public void setFree(int pos){
				VirtualIntBuffer ib = getMemoryInterface();
				if(pos >= ib.get(0)){
					println("BitmapHandler:setFree -> " + pos + " is out of bounds (" + ib.get(0) + ")");
					return;
				}
				ib.put(pos / 32, ib.get(pos / 32) & ~(1 << (pos % 32)));
			}
			public void setUse(int pos){
				VirtualIntBuffer ib = getMemoryInterface();
				if(pos >= ib.get(0)){
					println("BitmapHandler:setUse -> " + pos + " is out of bounds (" + ib.get(0) + ")");
					Thread.dumpStack();
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
				if(p == -1) println("BitmapHandler:allocateNewClustor ERROR, getNextEmptyBitPos() returned -1");
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
	}
}
