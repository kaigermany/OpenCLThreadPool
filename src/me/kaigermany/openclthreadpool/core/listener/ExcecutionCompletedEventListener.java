package me.kaigermany.openclthreadpool.core.listener;

public interface ExcecutionCompletedEventListener {
	public void onExcecutionCompleted(Object result, Throwable exception);
}
