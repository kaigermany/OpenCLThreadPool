
//todo: double-config!



__kernel void JavaEmulatorMain(__global int* memory, __global int* config){
	int threadId = get_global_id(0);
	int iterationsLeft = config[0];
	
	
	memory[2] = memory[0];
	
}
