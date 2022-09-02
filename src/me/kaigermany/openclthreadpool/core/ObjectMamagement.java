package me.kaigermany.openclthreadpool.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import me.kaigermany.openclthreadpool.core.MemoryModel.MinimalFS.Directory;

public class ObjectMamagement {
	private static final int SYSTEM_FS_DIR_NAME_THREADS = 1;
	private static final int SYSTEM_FS_DIR_NAME_CODEBOOK = 2;
	private static final int SYSTEM_FS_DIR_NAME_SHARED_DATA = 3;
	
	private static int classCounter=0;
	private static HashMap<String, Integer> knownClasses = new HashMap<String, Integer>();
	private static HashMap<Integer, HashMap<String, Integer>> knownMethods = new HashMap<Integer, HashMap<String, Integer>>();
	
	
	private static int compileClass(MemoryModel.MinimalFS system_fs, Class<?> clazz){
		//new Directory(ROOT_DIR_POS).get(BITMAP_NAME)
		MemoryModel.MinimalFS.Directory rootDir = system_fs.getDirectory(system_fs.ROOT_DIR_POS);
		//MemoryModel.MinimalFS.Directory codeBookDir = rootDir.getDirectory(SYSTEM_FS_DIR_NAME_CODEBOOK);
		MemoryModel.MinimalFS fs = new MemoryModel.MinimalFS(rootDir.getFile(SYSTEM_FS_DIR_NAME_CODEBOOK).getMemoryInterface());
		MemoryModel.MinimalFS.Directory codeBookDir = fs.getDirectory(fs.ROOT_DIR_POS);
		
		String name = clazz.getName();
		Integer uuid = knownClasses.get(name);
		if(uuid == null){
			classCounter++;
			knownClasses.put(name, classCounter);
			MemoryModel.MinimalFS.Directory entryLocation = fs.newDirectory(classCounter, codeBookDir.location);
			//codeBookDir.add(classCounter, entryLocation);
			
		}
		
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
