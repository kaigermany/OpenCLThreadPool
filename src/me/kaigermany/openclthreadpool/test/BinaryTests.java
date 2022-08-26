package me.kaigermany.openclthreadpool.test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;
import com.nativelibs4java.opencl.CLMem.Usage;
import com.nativelibs4java.opencl.CLPlatform.DeviceFeature;
import com.nativelibs4java.opencl.CLProgram;

// https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/opencl/Mandelbrot.java
// https://riptutorial.com/Download/opencl-de.pdf
// https://aparapi.com/documentation/ByteCode2OpenCL.pdf
// https://aparapi.com/documentation/converting-java-to-opencl.html
// https://registry.khronos.org/OpenGL-Refpages/gl4/html/floatBitsToInt.xhtml

public class BinaryTests {
	public static String loadSourceCode() {
		return "__kernel void func(__global int* ptr){"
	+ "\n	ptr[2] = ptr[0];"
	+ "\n	ptr[0] = 123;"
	//+ "\n	int test = (int*)ptr;"
	//+ "\n	test /= 3;"
	//+ "\n	*(int*)&ptr = test;"
	//+ "\n	ptr[9] = test;"
	+ "}";
	}

	public static void test() {
		CLContext context = JavaCL.createBestContext(DeviceFeature.DoubleSupport, DeviceFeature.GPU);
		CLQueue queue = context.createDefaultOutOfOrderQueueIfPossible();
		context = queue.getContext();
		String source = loadSourceCode();
		CLProgram program = context.createProgram(source);
		CLKernel kernel = program.createKernel("func");

		int length = 10;
		int cores = 1;
		ByteBuffer test_in_buf = ByteBuffer.allocate(length*4);
		IntBuffer test_in_buf3 = test_in_buf.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		test_in_buf3.put(12);
		test_in_buf3.put(54);
		test_in_buf3.rewind();
		CLBuffer<?> inBuf = context.createByteBuffer(Usage.InputOutput, test_in_buf, true); // true
		//CLBuffer<Double> outBuf = context.createDoubleBuffer(Usage.Output, length * 2);

		// Set the args of the kernel :
		kernel.setArgs(inBuf);

		// Ask for `length` parallel executions of the kernel in 1 dimension :
		CLEvent dftEvt = kernel.enqueueNDRange(queue, new int[] { cores });
		dftEvt.waitFor();
		// Return an NIO buffer read from the output CLBuffer :
		//Pointer<Double> temp = outBuf.read(queue, dftEvt);
		//Pointer<Double> temp = outBuf.read(queue, dftEvt);
		IntBuffer test_in_buf2 = inBuf.read(queue, dftEvt).getByteBuffer().asIntBuffer();//.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer(); 
		for(int i=0; i<length; i++) System.out.println("["+i+"]->"+test_in_buf2.get());
		
	}
}
