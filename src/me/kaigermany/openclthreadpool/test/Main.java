package me.kaigermany.openclthreadpool.test;

import org.lwjgl.opencl.CL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import me.kaigermany.openclthreadpool.core.MemoryModel;
import me.kaigermany.openclthreadpool.gpucontrol.GPURuntimeInterface;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {
	public static void main(String... args){
		System.out.println("<begin of test>");
		basicTest();
		System.out.println("<end of test>");
	}
	
	private static void basicTest(){
		 //CL.create();
		 //displayInfo();
		 if(true){
			 //ArrayGPU.main(null);
			 try{
				 //BinaryTests.test();
				 //AnalyzeAVGclassFrameSizes.test();
				 
				 
				 
				 //GPURuntimeInterface.execute();
				 MemoryModel.MinimalFS_unittest();
			 }catch(Throwable t){
				 t.printStackTrace();
			 }
			 return;
		 }
		MemoryStack stack = MemoryStack.stackPush();
		 
		 // https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/opencl/CLDemo.java
		 // https://searchcode.com/codesearch/view/72931173/
		 
		 
		 IntBuffer pi = stack.mallocInt(1);
	        checkCLError(CL10.clGetPlatformIDs(null, pi));
	        if (pi.get(0) == 0) {
	            throw new RuntimeException("No OpenCL platforms found.");
	        }

	        PointerBuffer platforms = stack.mallocPointer(pi.get(0));
	        checkCLError(CL10.clGetPlatformIDs(platforms, (IntBuffer)null));

	        PointerBuffer ctxProps = stack.mallocPointer(3);
	        ctxProps
	            .put(0, CL10.CL_CONTEXT_PLATFORM)
	            .put(2, 0);

	        IntBuffer errcode_ret = stack.callocInt(1);
	        for (int p = 0; p < platforms.capacity(); p++) {
	            long platform = platforms.get(p);
	            ctxProps.put(1, platform);

	            System.out.println("\n-------------------------");
	            System.out.printf("NEW PLATFORM: [0x%X]\n", platform);

	            CLCapabilities platformCaps = CL.createPlatformCapabilities(platform);

	            printPlatformInfo(platform, "CL_PLATFORM_PROFILE", CL10.CL_PLATFORM_PROFILE);
	            printPlatformInfo(platform, "CL_PLATFORM_VERSION", CL10.CL_PLATFORM_VERSION);
	            printPlatformInfo(platform, "CL_PLATFORM_NAME", CL10.CL_PLATFORM_NAME);
	            printPlatformInfo(platform, "CL_PLATFORM_VENDOR", CL10.CL_PLATFORM_VENDOR);
	            printPlatformInfo(platform, "CL_PLATFORM_EXTENSIONS", CL10.CL_PLATFORM_EXTENSIONS);
	            if (platformCaps.cl_khr_icd) {
	                printPlatformInfo(platform, "CL_PLATFORM_ICD_SUFFIX_KHR", KHRICD.CL_PLATFORM_ICD_SUFFIX_KHR);
	            }
	            System.out.println("");

	            checkCLError(CL10.clGetDeviceIDs(platform, CL10.CL_DEVICE_TYPE_ALL, null, pi));

	            PointerBuffer devices = stack.mallocPointer(pi.get(0));
	            checkCLError(CL10.clGetDeviceIDs(platform, CL10.CL_DEVICE_TYPE_ALL, devices, (IntBuffer)null));

	            for (int d = 0; d < devices.capacity(); d++) {
	                long device = devices.get(d);

	                CLCapabilities caps = CL.createDeviceCapabilities(device, platformCaps);

	                System.out.printf("\n\t** NEW DEVICE: [0x%X]\n", device);

	                System.out.println("\tCL_DEVICE_TYPE = " + getDeviceInfoLong(device, CL10.CL_DEVICE_TYPE));
	                System.out.println("\tCL_DEVICE_VENDOR_ID = " + getDeviceInfoInt(device, CL10.CL_DEVICE_VENDOR_ID));
	                System.out.println("\tCL_DEVICE_MAX_COMPUTE_UNITS = " + getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_COMPUTE_UNITS));
	                System.out
	                    .println("\tCL_DEVICE_MAX_WORK_ITEM_DIMENSIONS = " + getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS));
	                System.out.println("\tCL_DEVICE_MAX_WORK_GROUP_SIZE = " + getDeviceInfoPointer(device, CL10.CL_DEVICE_MAX_WORK_GROUP_SIZE));
	                System.out.println("\tCL_DEVICE_MAX_CLOCK_FREQUENCY = " + getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_CLOCK_FREQUENCY));
	                System.out.println("\tCL_DEVICE_ADDRESS_BITS = " + getDeviceInfoInt(device, CL10.CL_DEVICE_ADDRESS_BITS));
	                System.out.println("\tCL_DEVICE_AVAILABLE = " + (getDeviceInfoInt(device, CL10.CL_DEVICE_AVAILABLE) != 0));
	                System.out.println("\tCL_DEVICE_COMPILER_AVAILABLE = " + (getDeviceInfoInt(device, CL10.CL_DEVICE_COMPILER_AVAILABLE) != 0));

	                printDeviceInfo(device, "CL_DEVICE_NAME", CL10.CL_DEVICE_NAME);
	                printDeviceInfo(device, "CL_DEVICE_VENDOR", CL10.CL_DEVICE_VENDOR);
	                printDeviceInfo(device, "CL_DRIVER_VERSION", CL10.CL_DRIVER_VERSION);
	                printDeviceInfo(device, "CL_DEVICE_PROFILE", CL10.CL_DEVICE_PROFILE);
	                printDeviceInfo(device, "CL_DEVICE_VERSION", CL10.CL_DEVICE_VERSION);
	                printDeviceInfo(device, "CL_DEVICE_EXTENSIONS", CL10.CL_DEVICE_EXTENSIONS);
	                if (caps.OpenCL11) {
	                    printDeviceInfo(device, "CL_DEVICE_OPENCL_C_VERSION", CL11.CL_DEVICE_OPENCL_C_VERSION);
	                }

	                CLContextCallback contextCB;
	                long context = CL10.clCreateContext(ctxProps, device, contextCB = CLContextCallback.create((errinfo, private_info, cb, user_data) -> {
	                    System.err.println("[LWJGL] cl_context_callback");
	                    System.err.println("\tInfo: " + MemoryUtil.memUTF8(errinfo));
	                }), MemoryUtil.NULL, errcode_ret);
	                checkCLError(errcode_ret);

	                long buffer = CL10.clCreateBuffer(context, CL10.CL_MEM_READ_ONLY, 128, errcode_ret);
	                checkCLError(errcode_ret);

	                CLMemObjectDestructorCallback bufferCB1 = null;
	                CLMemObjectDestructorCallback bufferCB2 = null;

	                long subbuffer = MemoryUtil.NULL;

	                CLMemObjectDestructorCallback subbufferCB = null;

	                int errcode;

	                CountDownLatch destructorLatch;

	                if (caps.OpenCL11) {
	                    destructorLatch = new CountDownLatch(3);

	                    errcode = CL11.clSetMemObjectDestructorCallback(buffer, bufferCB1 = CLMemObjectDestructorCallback.create((memobj, user_data) -> {
	                        System.out.println("\t\tBuffer destructed (1): " + memobj);
	                        destructorLatch.countDown();
	                    }), MemoryUtil.NULL);
	                    checkCLError(errcode);

	                    errcode = CL11.clSetMemObjectDestructorCallback(buffer, bufferCB2 = CLMemObjectDestructorCallback.create((memobj, user_data) -> {
	                        System.out.println("\t\tBuffer destructed (2): " + memobj);
	                        destructorLatch.countDown();
	                    }), MemoryUtil.NULL);
	                    checkCLError(errcode);

	                    try (CLBufferRegion buffer_region = CLBufferRegion.malloc()) {
	                        buffer_region.origin(0);
	                        buffer_region.size(64);

	                        subbuffer = CL11.nclCreateSubBuffer(buffer,
	                        		CL10.CL_MEM_READ_ONLY,
	                            CL11.CL_BUFFER_CREATE_TYPE_REGION,
	                            buffer_region.address(),
	                            MemoryUtil.memAddress(errcode_ret));
	                        checkCLError(errcode_ret);
	                    }

	                    errcode = CL11.clSetMemObjectDestructorCallback(subbuffer, subbufferCB = CLMemObjectDestructorCallback.create((memobj, user_data) -> {
	                        System.out.println("\t\tSub Buffer destructed: " + memobj);
	                        destructorLatch.countDown();
	                    }), MemoryUtil.NULL);
	                    checkCLError(errcode);
	                } else {
	                    destructorLatch = null;
	                }

	                long exec_caps = getDeviceInfoLong(device, CL10.CL_DEVICE_EXECUTION_CAPABILITIES);
	                if ((exec_caps & CL10.CL_EXEC_NATIVE_KERNEL) == CL10.CL_EXEC_NATIVE_KERNEL) {
	                    System.out.println("\t\t-TRYING TO EXEC NATIVE KERNEL-");
	                    long queue = CL10.clCreateCommandQueue(context, device, MemoryUtil.NULL, errcode_ret);

	                    PointerBuffer ev = BufferUtils.createPointerBuffer(1);

	                    ByteBuffer kernelArgs = BufferUtils.createByteBuffer(4);
	                    kernelArgs.putInt(0, 1337);

	                    CLNativeKernel kernel;
	                    errcode = CL10.clEnqueueNativeKernel(queue, kernel = CLNativeKernel.create(
	                        args -> System.out.println("\t\tKERNEL EXEC argument: " + MemoryUtil.memByteBuffer(args, 4).getInt(0) + ", should be 1337")
	                    ), kernelArgs, null, null, null, ev);
	                    checkCLError(errcode);

	                    long e = ev.get(0);

	                    CountDownLatch latch = new CountDownLatch(1);

	                    CLEventCallback eventCB;
	                    errcode = CL11.clSetEventCallback(e, CL10.CL_COMPLETE, eventCB = CLEventCallback.create((event, event_command_exec_status, user_data) -> {
	                        System.out.println("\t\tEvent callback status: " + getEventStatusName(event_command_exec_status));
	                        latch.countDown();
	                    }), MemoryUtil.NULL);
	                    checkCLError(errcode);

	                    try {
	                        boolean expired = !latch.await(500, TimeUnit.MILLISECONDS);
	                        if (expired) {
	                            System.out.println("\t\tKERNEL EXEC FAILED!");
	                        }
	                    } catch (InterruptedException exc) {
	                        exc.printStackTrace();
	                    }
	                    eventCB.free();

	                    errcode = CL10.clReleaseEvent(e);
	                    checkCLError(errcode);
	                    kernel.free();

	                    kernelArgs = BufferUtils.createByteBuffer(MemoryStack.POINTER_SIZE * 2);

	                    kernel = CLNativeKernel.create(args -> {
	                    });

	                    long time   = System.nanoTime();
	                    int  REPEAT = 1000;
	                    for (int i = 0; i < REPEAT; i++) {
	                    	CL10.clEnqueueNativeKernel(queue, kernel, kernelArgs, null, null, null, null);
	                    }
	                    CL10.clFinish(queue);
	                    time = System.nanoTime() - time;

	                    System.out.printf("\n\t\tEMPTY NATIVE KERNEL AVG EXEC TIME: %.4fus\n", (double)time / (REPEAT * 1000));

	                    errcode = CL10.clReleaseCommandQueue(queue);
	                    checkCLError(errcode);
	                    kernel.free();
	                }

	                System.out.println();

	                if (subbuffer != MemoryUtil.NULL) {
	                    errcode = CL10.clReleaseMemObject(subbuffer);
	                    checkCLError(errcode);
	                }

	                errcode = CL10.clReleaseMemObject(buffer);
	                checkCLError(errcode);

	                if (destructorLatch != null) {
	                    // mem object destructor callbacks are called asynchronously on Nvidia

	                    try {
	                        destructorLatch.await();
	                    } catch (InterruptedException e) {
	                        e.printStackTrace();
	                    }

	                    subbufferCB.free();

	                    bufferCB2.free();
	                    bufferCB1.free();
	                }

	                errcode = CL10.clReleaseContext(context);
	                checkCLError(errcode);

	                contextCB.free();
	            }
	        }
	}
	
/*
    public static void displayInfo() {
        StringBuffer sb = new StringBuffer();

        for (int platformIndex = 0; platformIndex < CLPlatform.getPlatforms().size(); platformIndex++) {
            CLPlatform platform = CLPlatform.getPlatforms().get(platformIndex);
            System.out.println("Platform #" + platformIndex + ":" + platform.getInfoString(CL_PLATFORM_NAME));
            List<CLDevice> devices = platform.getDevices(CL_DEVICE_TYPE_ALL);
            for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                CLDevice device = devices.get(deviceIndex);
                sb.append(String.format("\nDevice #%d(%s):%s\n",
                        deviceIndex,
                        UtilCL.getDeviceType(device.getInfoInt(CL_DEVICE_TYPE)),
                        device.getInfoString(CL_DEVICE_NAME)));
                sb.append(String.format("\tCompute Units: %d @ %d MHz\n",
                        device.getInfoInt(CL_DEVICE_MAX_COMPUTE_UNITS), device.getInfoInt(CL_DEVICE_MAX_CLOCK_FREQUENCY)));
                sb.append(String.format("\tLocal memory: %s\n",
                        UtilCL.formatMemory(device.getInfoLong(CL_DEVICE_LOCAL_MEM_SIZE))));
                sb.append(String.format("\tGlobal memory: %s\n",
                        UtilCL.formatMemory(device.getInfoLong(CL_DEVICE_GLOBAL_MEM_SIZE))));
                sb.append("\n");
            }
        }
        System.out.println(sb.toString());
    }
    */
	
	
	

    static String getPlatformInfoStringASCII(long cl_platform_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            checkCLError(CL10.clGetPlatformInfo(cl_platform_id, param_name, (ByteBuffer)null, pp));
            int bytes = (int)pp.get(0);

            ByteBuffer buffer = stack.malloc(bytes);
            checkCLError(CL10.clGetPlatformInfo(cl_platform_id, param_name, buffer, null));

            return MemoryUtil.memASCII(buffer, bytes - 1);
        }
    }

    static String getPlatformInfoStringUTF8(long cl_platform_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            checkCLError(CL10.clGetPlatformInfo(cl_platform_id, param_name, (ByteBuffer)null, pp));
            int bytes = (int)pp.get(0);

            ByteBuffer buffer = stack.malloc(bytes);
            checkCLError(CL10.clGetPlatformInfo(cl_platform_id, param_name, buffer, null));

            return MemoryUtil.memUTF8(buffer, bytes - 1);
        }
    }

    static int getDeviceInfoInt(long cl_device_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pl = stack.mallocInt(1);
            checkCLError(CL10.clGetDeviceInfo(cl_device_id, param_name, pl, null));
            return pl.get(0);
        }
    }

    static long getDeviceInfoLong(long cl_device_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pl = stack.mallocLong(1);
            checkCLError(CL10.clGetDeviceInfo(cl_device_id, param_name, pl, null));
            return pl.get(0);
        }
    }

    static long getDeviceInfoPointer(long cl_device_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            checkCLError(CL10.clGetDeviceInfo(cl_device_id, param_name, pp, null));
            return pp.get(0);
        }
    }

    static String getDeviceInfoStringUTF8(long cl_device_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            checkCLError(CL10.clGetDeviceInfo(cl_device_id, param_name, (ByteBuffer)null, pp));
            int bytes = (int)pp.get(0);

            ByteBuffer buffer = stack.malloc(bytes);
            checkCLError(CL10.clGetDeviceInfo(cl_device_id, param_name, buffer, null));

            return MemoryUtil.memUTF8(buffer, bytes - 1);
        }
    }

    static int getProgramBuildInfoInt(long cl_program_id, long cl_device_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pl = stack.mallocInt(1);
            checkCLError(CL10.clGetProgramBuildInfo(cl_program_id, cl_device_id, param_name, pl, null));
            return pl.get(0);
        }
    }

    static String getProgramBuildInfoStringASCII(long cl_program_id, long cl_device_id, int param_name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            checkCLError(CL10.clGetProgramBuildInfo(cl_program_id, cl_device_id, param_name, (ByteBuffer)null, pp));
            int bytes = (int)pp.get(0);

            ByteBuffer buffer = stack.malloc(bytes);
            checkCLError(CL10.clGetProgramBuildInfo(cl_program_id, cl_device_id, param_name, buffer, null));

            return MemoryUtil.memASCII(buffer, bytes - 1);
        }
    }

    static void checkCLError(IntBuffer errcode) {
        checkCLError(errcode.get(errcode.position()));
    }

    static void checkCLError(int errcode) {
        if (errcode != CL10.CL_SUCCESS) {
            throw new RuntimeException(String.format("OpenCL error [%d]", errcode));
        }
    }
    private static void printDeviceInfo(long device, String param_name, int param) {
        System.out.println("\t" + param_name + " = " + getDeviceInfoStringUTF8(device, param));
    }

    private static String getEventStatusName(int status) {
        switch (status) {
            case CL11.CL_QUEUED:
                return "CL_QUEUED";
            case CL11.CL_SUBMITTED:
                return "CL_SUBMITTED";
            case CL11.CL_RUNNING:
                return "CL_RUNNING";
            case CL11.CL_COMPLETE:
                return "CL_COMPLETE";
            default:
                throw new IllegalArgumentException(String.format("Unsupported event status: 0x%X", status));
        }
    }
    private static void printPlatformInfo(long platform, String param_name, int param) {
        System.out.println("\t" + param_name + " = " + getPlatformInfoStringUTF8(platform, param));
    }
}
