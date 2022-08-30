package me.kaigermany.openclthreadpool.core;

import java.util.HashMap;

public class ObjectMamagement {
	private static HashMap<String, Integer> knownClasses = new HashMap<String, Integer>();
	private static HashMap<Integer, HashMap<String, Integer>> knownMethods = new HashMap<Integer, HashMap<String, Integer>>();
	
	private static int compileClass(Class<?> clazz){
		
		
		return -1;
	}
	
	private static void writeObject(MemoryModel.MinimalFS fs, MemoryModel.MinimalFS.File targetFile, Object obj){
		
	}
	
	private static Object readObject(MemoryModel.MinimalFS fs, MemoryModel.MinimalFS.File targetFile){
		
		
		return null;
	}
}
