package me.kaigermany.openclthreadpool.gpucontrol;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;
import com.nativelibs4java.opencl.CLMem.Usage;
import com.nativelibs4java.opencl.CLPlatform.DeviceFeature;

public class GPURuntimeInterface {
	private static CLContext context;
	private static CLQueue queue;
	private static CLKernel kernel;
	private static IntBuffer memory;
	private static ByteBuffer memory_ByteBufferPointer;
	private static IntBuffer configBuffer;
	private static ByteBuffer configBuffer_ByteBufferPointer;
	private static CLBuffer<?> clBuffer;
	private static CLBuffer<?> maxIterationCount;
	private static int currentThreadCount = 0;
	
	static{
		try{
			context = JavaCL.createBestContext(DeviceFeature.DoubleSupport, DeviceFeature.GPU);
			queue = context.createDefaultOutOfOrderQueueIfPossible();
			context = queue.getContext();
			String source;
			{
				InputStream is = GPURuntimeInterface.class.getClassLoader().getResourceAsStream("me/kaigermany/openclthreadpool/gpucontrol/NativeJavaRuntime.cl");
				ByteArrayOutputStream baos = new ByteArrayOutputStream(1 << 16);
				byte[] arr = new byte[1 << 16];
				int l;
				while((l = is.read(arr)) != -1) baos.write(arr, 0, l);
				is.close();
				source = new String(baos.toByteArray());
			}
			CLProgram program = context.createProgram(source);
			kernel = program.createKernel("JavaEmulatorMain");
			realloc(1 << 16);
			updateConfig();
			setIterationCount(1000);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static void setIterationCount(int val){
		configBuffer.put(0, val);
		updateConfig();
	}
	
	public static void setClusterSize(int val){
		configBuffer.put(1, val);
		updateConfig();
	}
	private static void updateConfig(){
		if(maxIterationCount != null) maxIterationCount.release();
		configBuffer_ByteBufferPointer = ByteBuffer.allocate(4);
		configBuffer = configBuffer_ByteBufferPointer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		maxIterationCount = context.createByteBuffer(Usage.Input, configBuffer_ByteBufferPointer, true);
	}
	
	public static void realloc(int size){
		IntBuffer old = memory;
		memory_ByteBufferPointer = ByteBuffer.allocate(size*4);
		memory = memory_ByteBufferPointer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		if(old != null){
			old.rewind();
			int len = old.remaining();
			int rp = 0;
			int[] buffer = new int[1 << 20];
			int l;
			while(rp < len){
				l = Math.min(len - rp, buffer.length);
				old.get(buffer, rp, l);
				memory.put(buffer, rp, l);
			}
		}
		if(clBuffer != null) clBuffer.release();
		clBuffer = context.createByteBuffer(Usage.InputOutput, memory_ByteBufferPointer, true);
	}
	
	public static void execute(){
		insertNewThreads();
		kernel.setArgs(clBuffer, maxIterationCount);
		CLEvent dftEvt = kernel.enqueueNDRange(queue, new int[] { currentThreadCount });
		dftEvt.waitFor();
		clBuffer.read(queue, dftEvt).getInts(memory);
		fetchDoneThreads();
	}

	private static void fetchDoneThreads() {
		// TODO Auto-generated method stub
		
	}

	private static void insertNewThreads() {
		// TODO Auto-generated method stub
		
	}
}
