
//todo: double-config:
// Enable double-precision floating point numbers support.
// Not all platforms / devices support this, so you may have to switch to floats.
#pragma OPENCL EXTENSION cl_khr_fp64 : enable


__kernel void JavaEmulatorMain(__global int* memory, __global int* config){
	int threadId = get_global_id(0);
	int iterationsLeft = config[0];
	
	int threadName = 12345;
	int ROOT_DIR_POS = 1;
	//int filePos = newFile(memory, threadName, 5678, ROOT_DIR_POS);
	//int filePos = allocateNewCluster(memory, threadName);
	//int filePos = BitmapHandler_getSize(memory, threadName, 2);
	int filePos = BitmapHandler_allocateNewCluster(memory, threadName, 2);
	//memory[2] = memory[0];
	memory[7] = filePos;
}

/*
planed disk layout:
system fs:
root:[bitmap<FILE>, threads<DIR>, codeBook<DIR>, sharedData<DIR>]
	threads:[thread0, thread1, thread2, ...]
		thread:[properties<FILE>, stack<DIR>]
		
	codeBook:[class0, class1, class2, ...]
		class:[field0, field1, field2, ...]
			field<FILE>:[args-count, exception-table-length, exception-table<int>[], code-length, code<int>[]]
				exception-table
				
	sharedData:[class0, class1, class2, ...]
		class:[field0, field1, field2, ...]
*/

