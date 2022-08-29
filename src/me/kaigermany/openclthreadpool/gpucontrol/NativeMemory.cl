// my biggest nightmare just became true -> https://stackoverflow.com/questions/21329060/opencl-and-indirect-recursion


int BitmapHandler_getNextEmptyBitPos(int bitMask){
	for(int i=0; i<32; i++) if((bitMask & (1 << i)) == 0) return i;
	return -1;
}

int getNextExtension_ParentFS(__global int* memory, int location){
	int parentClusterSize = memory[0];//memory.get(0);
	return memory[location*parentClusterSize + 1];
}

int Extensionbased_get_ParentFS(__global int* memory, int location, int pos){
	int parentClusterSize = memory[0];
	int spaceSize = parentClusterSize - 2;
	int currentEntryLocation = location;
	while(pos >= spaceSize){
		currentEntryLocation = getNextExtension_ParentFS(memory, currentEntryLocation);
		if(currentEntryLocation == 0){
			return 0;
		}
		pos -= spaceSize;
	}
	return memory[currentEntryLocation*parentClusterSize + pos + 2];
}

int findDirEntry_ParentFS(__global int* memory, int dir_location, int entryName){
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

int RunLength_getImpl_ParentFS(__global int* memory, int file_location, int virualizedIndex){
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

bool RunLengthInterface_put_ParentFS(__global int* memory, int file_location, int pos, int val) {
	int parentClusterSize = memory[0];
	//println("RunLengthController:getMemoryInterface$put("+pos+", "+val+")");
	int cluster = RunLength_getImpl_ParentFS(memory, file_location, pos / parentClusterSize);
	//if(pos == 32) System.out.println("cluster="+cluster);
	if(cluster == -1) return false;
	//memory.put(cluster*parentClusterSize + (pos % parentClusterSize), val);
	memory[cluster*parentClusterSize + (pos % parentClusterSize)] = val;
	return true;
}

int RunLengthInterface_get_ParentFS(__global int* memory, int file_location, int pos) {
	int parentClusterSize = memory[0];
	int cluster = RunLength_getImpl_ParentFS(memory, file_location, pos / parentClusterSize);
	if(cluster == -1) return 0;
	//return memory.get(cluster*parentClusterSize + (pos % parentClusterSize));
	return memory[cluster*parentClusterSize + (pos % parentClusterSize)];
}

int readFromParentFS(__global int* memory, int threadName, int pos){
	int parentRootDirPos = memory[1];
	int fileEntryPointer = findDirEntry_ParentFS(memory, parentRootDirPos, threadName);
	return RunLengthInterface_get_ParentFS(memory, fileEntryPointer, pos);
}

bool writeToParentFS(__global int* memory, int threadName, int pos, int val){
	int parentRootDirPos = memory[1];
	int fileEntryPointer = findDirEntry_ParentFS(memory, parentRootDirPos, threadName);
	return RunLengthInterface_put_ParentFS(memory, fileEntryPointer, pos, val);
}

int Entry_getObjectType(__global int* memory, int threadName, int entry_location){//0=undefined, 1=File, 2=Dir, 3=Extension of File or Dir-Container
	int clusterSize = readFromParentFS(memory, threadName, 0);
	return readFromParentFS(memory, threadName, entry_location*clusterSize);
}

int Entry_getNextExtension(__global int* memory, int threadName, int entry_location){
	int clusterSize = readFromParentFS(memory, threadName, 0);
	return readFromParentFS(memory, threadName, entry_location*clusterSize + 1);
}

bool Entry_setNextExtension(__global int* memory, int threadName, int entry_location, int nextEntry_location) {
	int clusterSize = readFromParentFS(memory, threadName, 0);
	return writeToParentFS(memory, threadName, entry_location*clusterSize + 1, nextEntry_location);
}

bool Entry_setObjectType(__global int* memory, int threadName, int entry_location, int typeId) {
	int clusterSize = readFromParentFS(memory, threadName, 0);
	return writeToParentFS(memory, threadName, entry_location*clusterSize, typeId);
}

bool wipeCluster(__global int* memory, int threadName, int offset){
	int clusterSize = readFromParentFS(memory, threadName, 0);
	offset *= clusterSize;
	for(int i=0; i<clusterSize; i++) if(!writeToParentFS(memory, threadName, i + offset, 0)) return false;
	return true;
}

int Entry_Extensionbased_get(__global int* memory, int threadName, int entry_location, int pos) {
	int clusterSize = readFromParentFS(memory, threadName, 0);
	int spaceSize = clusterSize - 2;
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

int RunLength_getSize(__global int* memory, int threadName, int File_location){
	return Entry_Extensionbased_get(memory, threadName, File_location, 0);
}

int RunLength_get(__global int* memory, int threadName, int File_location, int virualizedIndex){
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

bool BitmapHandler_setUse(__global int* memory, int threadName, int File_location, int pos);
int Directory_get(__global int* memory, int threadName, int Directory_location, int entryNameId);

int File_Storage_get(__global int* memory, int threadName, int File_location, int pos) {
	int clusterSize = readFromParentFS(memory, threadName, 0);
	int cluster = RunLength_get(memory, threadName, File_location, pos / clusterSize);
	if(cluster == -1) return 0;
	return readFromParentFS(memory, threadName, cluster*clusterSize + (pos % clusterSize));
}

int BitmapHandler_getSize(__global int* memory, int threadName, int File_location){
	return File_Storage_get(memory, threadName, File_location, 0);
}

int BitmapHandler_allocateNewCluster(__global int* memory, int threadName, int File_location) {
	//int debugCounter = 16*15;

	int offset = 1;
	int lastMask;
	while((lastMask = File_Storage_get(memory, threadName, File_location, offset)) == -1) offset++;
	int p = BitmapHandler_getNextEmptyBitPos(lastMask);
	if(p == -1) return -1;
	offset = ((offset-1) * 32) + p;
	if(offset >= BitmapHandler_getSize(memory, threadName, File_location)) return -1;
	//memory[3] = 123456;
	if(!wipeCluster(memory, threadName, offset)) {
	memory[3] = 567;
		return -1;
	} //if this operation fail, it will not store this attempt to claim the cluster!
	//return 456;
	memory[4] = 1234567;
	//if(!BitmapHandler_setUse(memory, threadName, File_location, offset)) return -1;
	return offset;
}

bool File_Storage_put_noNewClusterClaim(__global int* memory, int threadName, int File_location, int pos, int val) {
	int clusterSize = readFromParentFS(memory, threadName, 0);
	int cluster;
	while(true){
		cluster = RunLength_get(memory, threadName, File_location, pos / clusterSize);
		if(cluster != -1) return false;
	}
	return writeToParentFS(memory, threadName, cluster*clusterSize + (pos % clusterSize), val);
}
/*
bool BitmapHandler_initialize(__global int* memory, int threadName, int File_location){
	return File_Storage_put(memory, threadName, File_location, 1, 15);//mark the first 4 clusters as used.
}
*/
bool BitmapHandler_setSize(__global int* memory, int threadName, int File_location, int s){
	return File_Storage_put_noNewClusterClaim(memory, threadName, File_location, 0, s);
}

bool BitmapHandler_setUse(__global int* memory, int threadName, int File_location, int pos){
	memory[5] = 1234;
	if(pos >= File_Storage_get(memory, threadName, File_location, 0)){
		return true;
	}
	int wp = (pos / 32) + 1;
	return File_Storage_put_noNewClusterClaim(memory, threadName, File_location, wp, File_Storage_get(memory, threadName, File_location, wp) | (1 << (pos % 32)));
}

bool BitmapHandler_setFree(__global int* memory, int threadName, int File_location, int pos){
	if(pos >= File_Storage_get(memory, threadName, File_location, 0)){
		return true;
	}
	return File_Storage_put_noNewClusterClaim(memory, threadName, File_location, pos / 32, File_Storage_get(memory, threadName, File_location, pos / 32) & ~(1 << (pos % 32)));
}

bool deallocateNewCluster(__global int* memory, int threadName, int clusterId){
	return BitmapHandler_setFree(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME), clusterId);
}

bool overrideBitmapSize(__global int* memory, int threadName, int newLen){
	return BitmapHandler_setSize(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME), newLen);
}

int Directory_getSize(__global int* memory, int threadName, int Directory_location){
	int p = 0;
	while(Entry_Extensionbased_get(memory, threadName, Directory_location, p) != 0){
		p+=2;
	}
	return p / 2;
}

int allocateNewCluster(__global int* memory, int threadName){
	return BitmapHandler_allocateNewCluster(memory, threadName, Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME));
}

bool Entry_Extensionbased_put(__global int* memory, int threadName, int entry_location, int pos, int val) {
	int clusterSize = readFromParentFS(memory, threadName, 0);
	//System.out.println("ExtensionbasedIntBuffer:put("+pos+", "+val+")");
	int spaceSize = clusterSize - 2;
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

bool RunLength_append(__global int* memory, int threadName, int File_location, int realIndex){
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

bool File_Storage_put(__global int* memory, int threadName, int File_location, int pos, int val) {
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



bool Directory_add(__global int* memory, int threadName, int Directory_location, int entryNameId, int entryLocation){
	int p = Directory_getSize(memory, threadName, Directory_location) * 2;
	if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p, entryNameId)) return false;
	if(!Entry_Extensionbased_put(memory, threadName, Directory_location, p+1, entryLocation)) return false;
	return true;
}

bool Directory_remove(__global int* memory, int threadName, int Directory_location, int entryNameId){
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

int Directory_get(__global int* memory, int threadName, int Directory_location, int entryNameId){
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

















bool init_makeGenericEntry(__global int* memory, int threadName, int clusterSize, int clusterIndex, int entryType, bool wipeSpace) {
	if(!writeToParentFS(memory, threadName, clusterSize*clusterIndex, entryType)) return false;
	if(!writeToParentFS(memory, threadName, clusterSize*clusterIndex +1, 0)) return false;
	if(wipeSpace){
		for(int i=2; i<clusterSize; i++) if(!writeToParentFS(memory, threadName, clusterSize*clusterIndex +i, 0)) return false;
	}
	return true;
}
/*
bool initSubFS(__global int* memory, int threadName, int clusterSize, int usableSpace){
	int clusterCount = usableSpace / clusterSize;
	//memory.put(0, clusterSize);
	//memory.put(1, ROOT_DIR_POS);
	memory[0] = clusterSize;
	memory[0] = ROOT_DIR_POS;
	if(!init_makeGenericEntry(memory, threadName, clusterSize, ROOT_DIR_POS, 2, true)) return false;//mft-root
	if(!init_makeGenericEntry(memory, threadName, clusterSize, BITMAP_POS, 1, true)) return false;//bitmap-entry
	if(!Directory_add(memory, threadName, ROOT_DIR_POS, BITMAP_NAME, BITMAP_POS)) return false;//interface mft-root(location=1), add bitmap-entry(will be located at pos 2)
	//write bitmap-file
	//__global int* ib = new File(BITMAP_POS).getMemoryInterface();
	//BitmapHandler b = new BitmapHandler(BITMAP_POS);
	//b.rlc.writeRawInitaialData(new int[]{1, 3, 1});
	//__global int* vib = b.getExtensionbasedIntBufferInterface();
	if(!Entry_Extensionbased_put(memory, threadName, BITMAP_POS, 0, 1)) return false;
	if(!Entry_Extensionbased_put(memory, threadName, BITMAP_POS, 1, 3)) return false;
	if(!Entry_Extensionbased_put(memory, threadName, BITMAP_POS, 2, 1)) return false;
	if(!BitmapHandler_setSize(memory, threadName, BITMAP_POS, clusterCount)) return false;
	return BitmapHandler_initialize(memory, threadName, BITMAP_POS);
	//return true;
}
*/

//void removeEntry(__global int* memory, int threadName, int entry_location);

void removeFile(__global int* memory, int threadName, int File_location){
	int bitmapPos = Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME);
	int len = RunLength_getSize(memory, threadName, File_location);
	//println("RunLengthController:get -> len="+len);
	for(int i=1; i<len*2; i+=2){
		int currRealOffset = Entry_Extensionbased_get(memory, threadName, File_location, i);
		int currRunLength = Entry_Extensionbased_get(memory, threadName, File_location, i + 1);
		for(int pos=0; pos<currRunLength; pos++) BitmapHandler_setFree(memory, threadName, bitmapPos, pos+currRealOffset);
	}
}

/*
void removeDir(__global int* memory, int threadName, int dir_location){
	int p = 0;
	int ptr;
	while((ptr = Entry_Extensionbased_get(memory, threadName, dir_location, p)) != 0){
		p+=2;
		removeEntry(memory, threadName, ptr);
	}
}

void removeEntry(__global int* memory, int threadName, int entry_location){
	if(Entry_getObjectType(memory, threadName, entry_location) == 2){
		removeDir(memory, threadName, entry_location);
	} else {
		removeFile(memory, threadName, entry_location);
	}
	//free extension chain
	int currentEntry = entry_location;
	int bitmapPos = Directory_get(memory, threadName, ROOT_DIR_POS, BITMAP_NAME);
	while(true){
		int nextEntry = Entry_getNextExtension(memory, threadName, currentEntry);
		BitmapHandler_setFree(memory, threadName, bitmapPos, currentEntry);
		if(nextEntry == 0){
			return;
		}
		currentEntry = nextEntry;
	}
}
*/



int newFile(__global int* memory, int threadName, int uuid, int targetDirToStore){
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


/*

bool RunLength_writeRawInitaialData(__global int* memory, int threadName, int File_location, int[] data){
	for(int i=0; i<data.length; i++){
		if(!Entry_Extensionbased_put(memory, threadName, File_location, i, data[i])) return false;
	}
	return true;
}

int[] Directory_getAll(__global int* memory, int threadName, int Directory_location){
	int end = Directory_getSize(memory, threadName, Directory_location) * 2;
	int[] out = new int[end];
	for(int i=0; i<end; i++) out[i] = Entry_Extensionbased_get(memory, threadName, Directory_location, i);
	return out;
}

int[] RunLength_getAll(__global int* memory, int threadName, int File_location){
	int[] out = new int[RunLength_getSize(memory, threadName, File_location) * 2];
	//println(""+out.length);
	for(int i=0; i<out.length; i++) out[i] = Entry_Extensionbased_get(memory, threadName, File_location, i + 1);
	return out;
}
*/

