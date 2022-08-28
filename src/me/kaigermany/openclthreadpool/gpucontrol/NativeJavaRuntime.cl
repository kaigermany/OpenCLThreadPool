
//todo: double-config:
// Enable double-precision floating point numbers support.
// Not all platforms / devices support this, so you may have to switch to floats.
#pragma OPENCL EXTENSION cl_khr_fp64 : enable


__kernel void JavaEmulatorMain(__global int* memory, __global int* config){
	int threadId = get_global_id(0);
	int iterationsLeft = config[0];
	
	
	memory[2] = memory[0];
	
}
