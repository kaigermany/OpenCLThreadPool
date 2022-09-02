package me.kaigermany.openclthreadpool.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import me.kaigermany.openclthreadpool.core.MemoryModel.MinimalFS.Directory;

public class ObjectMamagement {
	private static HashMap<String, Integer> knownClasses = new HashMap<String, Integer>();
	private static HashMap<Integer, HashMap<String, Integer>> knownMethods = new HashMap<Integer, HashMap<String, Integer>>();
	
	private static int compileClass(MemoryModel.MinimalFS system_fs, Class<?> clazz){
		new Directory(ROOT_DIR_POS).get(BITMAP_NAME)
		clazz = clazz.getSuperclass();
		if(clazz != Object.class) compileClass(system_fs, clazz);
		return -1;
	}
	
	private static void writeObject(MemoryModel.MinimalFS fs, MemoryModel.MinimalFS.File targetFile, Object obj){
		if(obj == null){
			
		} else {
			ArrayList<Field> fields = getAllFields(obj.getClass());
		}
	}
	
	private static Object readObject(MemoryModel.MinimalFS fs, MemoryModel.MinimalFS.File targetFile){
		
		
		return null;
	}
	
	private static ArrayList<Field> getAllFields(Class<?> c){
		ArrayList<Field> out = new ArrayList<Field>();
		do{
			Field[] fields = c.getDeclaredFields();
			c = c.getSuperclass();
			for(Field f : fields) out.add(f);
		}while(c != Object.class);
		return out;
	}
}
