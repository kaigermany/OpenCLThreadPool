package me.kaigermany.openclthreadpool.gpucontrol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.CLMem.Usage;

public class BufferInterface {
	private int length;
	private CLBuffer<?> buffer;
	private MinimalFS.VirtualIntBuffer localBufferInterface;
	private CLQueue queue;
	
	public BufferInterface(CLContext context, CLQueue queue, int size){
		this.length = size;
		this.queue = queue;
		this.buffer = context.createByteBuffer(Usage.InputOutput, size*4);
		this.localBufferInterface = new LocalBuffer();
	}
	
	public CLBuffer<?> getBuffer(){
		return buffer;
	}
	
	public MinimalFS.VirtualIntBuffer getLocalBufferInterface(){
		return localBufferInterface;
	}
	
	public class LocalBuffer implements MinimalFS.VirtualIntBuffer {

		@Override
		public int get(int pos) {
			if(pos < 0 || pos >= length) return 0;
			try{
				//ByteBuffer bb = ByteBuffer.allocate(4);
				//buffer.read(queue, pos*4, 4, bb, false);
				return buffer.read(queue).getIntAtOffset(pos*4L);
				/*
				int[] dest = new int[1];
				buffer.read(queue).getIntsAtOffset(pos*4L, dest, 0, length);
				return dest[0];
				*/
				//return bb.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get();
			}catch(Exception e){
				return 0;
			}
		}

		@Override
		public boolean put(int pos, int val) {
			if(pos < 0 || pos >= length) return false;
			try{
				ByteBuffer bb = ByteBuffer.allocate(4);
				bb.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(val);
				buffer.writeBytes(queue, pos*4, 4, bb, false);
				return true;
			}catch(Exception e){
				return false;
			}
		}
		
	}
}
