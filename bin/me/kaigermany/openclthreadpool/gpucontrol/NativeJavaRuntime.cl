
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
