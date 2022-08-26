package me.kaigermany.openclthreadpool.test;

import java.nio.DoubleBuffer;

import org.bridj.Pointer;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem.Usage;
import com.nativelibs4java.opencl.CLPlatform.DeviceFeature;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;
// https://ochafik.com/p_501
// www.java2s.com/ref/jar/lwjgl-3.0.0a.jar.zip
// https://jar-download.com/artifacts/com.nativelibs4java/javacl
public class ArrayGPU {
	/**
	 * The source code of the OpenCL program
	 */
	private static String programSource = "__kernel void " + "sampleKernel(__global const float *a,"
			+ "             __global const float *b," + "             __global float *c)" + "{"
			+ "    int gid = get_global_id(0);" + "    c[gid] = a[gid] + b[gid];" + "}";
	static CLQueue queue;
	static CLContext context;
	static CLProgram program;
	static CLKernel kernel;

	public static void main(String args[]) {

		
		
		
		// Create a context with the best double numbers support possible :
				// (try using DeviceFeature.GPU, DeviceFeature.CPU...)
				CLContext context = JavaCL.createBestContext(DeviceFeature.DoubleSupport, DeviceFeature.GPU);

				// Create a command queue, if possible able to execute multiple jobs in
				// parallel
				// (out-of-order queues will still respect the CLEvent chaining)
				CLQueue queue = context.createDefaultOutOfOrderQueueIfPossible();
				ArrayGPU.queue = queue;
				ArrayGPU.context = queue.getContext();
				String source = loadSourceCode();
				program = context.createProgram(source);
				kernel = program.createKernel("dft");
				//DFT dft = new DFT(queue);
				// DFT2 dft = new DFT2(queue);

				// Create some fake test data :
				double[] in = createTestDoubleData();

				// Transform the data (spatial -> frequency transform) :
				double[] transformed = dft(in, true);

				for (int i = 0; i < transformed.length / 2; i++) {
					// Print the transformed complex values (real + i * imaginary)
					System.out.println(transformed[i * 2] + "\t + \ti * " + transformed[i * 2 + 1]);
				}

				// Reverse-transform the transformed data (frequency -> spatial
				// transform) :
				//double[] backTransformed = dft(DoubleBuffer.wrap(transformed), false);
				DoubleBuffer buffer = dft(DoubleBuffer.wrap(transformed), false);
				buffer.rewind();
				double[] backTransformed = new double[buffer.remaining()];
				buffer.get(backTransformed);
				// Check the transform + inverse transform give the original data back :
				double precision = 1e-5;
				for (int i = 0; i < in.length; i++) {
					if (Math.abs(in[i] - backTransformed[i]) > precision)
						throw new RuntimeException("Different values in back-transformed array than in original array !");
				}
	}

	/**
	 * Method that takes complex values in input (sequence of pairs of real and
	 * imaginary values) and returns the Discrete Fourier Transform of these
	 * values if forward == true or the inverse transform if forward == false.
	 */
	public static synchronized DoubleBuffer dft(DoubleBuffer in, boolean forward) {
		assert in.capacity() % 2 == 0;
		int length = in.capacity() / 2;

		// Create an input CLBuffer that will be a copy of the NIO buffer :
		CLBuffer<Double> inBuf = context.createDoubleBuffer(Usage.Input, in, true); // true
																						// =
																						// copy

		// Create an output CLBuffer :
		CLBuffer<Double> outBuf = context.createDoubleBuffer(Usage.Output, length * 2);

		// Set the args of the kernel :
		kernel.setArgs(inBuf, outBuf, length, forward ? 1 : -1);

		// Ask for `length` parallel executions of the kernel in 1 dimension :
		CLEvent dftEvt = kernel.enqueueNDRange(queue, new int[] { length });

		// Return an NIO buffer read from the output CLBuffer :
		Pointer<Double> temp = outBuf.read(queue, dftEvt);
		
		return temp.getDoubleBuffer();
	}

	/// Wrapper method that takes and returns double arrays
	public static double[] dft(double[] complexValues, boolean forward) {
		DoubleBuffer outBuffer = dft(DoubleBuffer.wrap(complexValues), forward);
		double[] out = new double[complexValues.length];
		outBuffer.get(out);
		return out;
	}
	
	static double[] createTestDoubleData() {
		int n = 32;
		double[] in = new double[2 * n];

		for (int i = 0; i < n; i++) {
			in[i * 2] = 1 / (double) (i + 1);
			in[i * 2 + 1] = 0;
		}
		return in;
	}
	
	public static String loadSourceCode(){
		return "// Enable double-precision floating point numbers support."+
"\n// Not all platforms / devices support this, so you may have to switch to floats."+
"\n#pragma OPENCL EXTENSION cl_khr_fp64 : enable"+
"\n"+
"\n__kernel void dft("+
"\n	__global const double2 *in, // complex values input"+
"\n	__global double2 *out,      // complex values output"+
"\n	int length,                 // number of input and output values"+
"\n	int sign)                   // sign modifier in the exponential :"+
"\n	                            // 1 for forward transform, -1 for backward."+
"\n{"+
"\n	// Get the varying parameter of the parallel execution :"+
"\n	int i = get_global_id(0);"+
"\n	"+
"\n	// In case we're executed 'too much', check bounds :"+
"\n	if (i >= length)"+
"\n		return;"+
"\n	"+
"\n	// Initialize sum and inner arguments"+
"\n	double2 tot = 0;"+
"\n	double param = (-2 * sign * i) * M_PI / (double)length;"+
"\n	"+
"\n	for (int k = 0; k < length; k++) {"+
"\n		double2 value = in[k];"+
"\n		"+
"\n		// Compute sin and cos in a single call : "+
"\n		double c;"+
"\n		double s = sincos(k * param, &c);"+
"\n		"+
"\n		// This adds (value.x * c - value.y * s, value.x * s + value.y * c) to the sum :"+
"\n		tot += (double2)("+
"\n			dot(value, (double2)(c, -s)), "+
"\n			dot(value, (double2)(s, c))"+
"\n		);"+
"\n	}"+
"\n	"+
"\n	if (sign == 1) {"+
"\n		// forward transform (space -> frequential)"+
"\n		out[i] = tot;"+
"\n	} else {"+
"\n		// backward transform (frequential -> space)"+
"\n		out[i] = tot / (double)length;"+
"\n	}"+
"\n}"+
"\n";
	}

}