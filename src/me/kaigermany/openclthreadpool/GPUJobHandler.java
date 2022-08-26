package me.kaigermany.openclthreadpool;

import java.lang.reflect.Method;
import java.util.ArrayList;

import me.kaigermany.openclthreadpool.core.ClassTranspiler;
import me.kaigermany.openclthreadpool.core.listener.ExcecutionCompletedEventListener;

public class GPUJobHandler implements Runnable {
	private static ArrayList<ExecutableFrameContainer> frameBuffer = new ArrayList<ExecutableFrameContainer>();
	private static Thread jobSheduler;
	
	static{
		jobSheduler = new Thread(new GPUJobHandler(), "GPUJobSheduler");
	}
	
	public static Object exceuteMethod(Method method, Object... args) throws Throwable {
		final ResponceContainer container = new ResponceContainer(Thread.currentThread());
		
		ExcecutionCompletedEventListener callback = new ExcecutionCompletedEventListener() {
			@Override
			public void onExcecutionCompleted(Object result, Throwable exception) {
				container.result = result;
				container.exception = exception;
				container.wakeUpThread.interrupt();
			}
		};
		
		exceuteMethod(method, callback, args);
		
		try {
			Thread.sleep(Long.MAX_VALUE);
		} catch (InterruptedException e) {}
		
		if(container.exception != null){
			throw container.exception;
		}
		return container.result;
	}
	
	public static void exceuteMethod(Method method, ExcecutionCompletedEventListener callback, Object... args){
		ExecutableFrameContainer frame = new ExecutableFrameContainer(method, callback, args);
		synchronized (frameBuffer) {
			frameBuffer.add(frame);
		}
	}
	
	public static class ResponceContainer{
		public Object result;
		public Throwable exception;
		public Thread wakeUpThread;
		
		public ResponceContainer(Thread thread){
			wakeUpThread = thread;
		}
	}
	
	public static class ExecutableFrameContainer{
		public Method method;
		public ExcecutionCompletedEventListener eventCallback;
		public Object[] args;
		
		public ExecutableFrameContainer(Method method, ExcecutionCompletedEventListener eventCallback, Object[] args){
			this.method = method;
			this.eventCallback = eventCallback;
			this.args = args;
		}
	}

	@Override
	public void run() {
		while(true){
			ExecutableFrameContainer nextJob = null;
			synchronized (frameBuffer) {
				if(frameBuffer.size() > 0) nextJob = frameBuffer.remove(0);
			}
			Object compiledClasses = ClassTranspiler.transpile(nextJob.method);
			//compiledClasses.upload(nextJob.args);
			//compiledClasses.invoke(nextJob.eventCallback);
		}
	}
	
	
}
