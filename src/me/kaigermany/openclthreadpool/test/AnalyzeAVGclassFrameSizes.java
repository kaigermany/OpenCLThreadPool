package me.kaigermany.openclthreadpool.test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class AnalyzeAVGclassFrameSizes {
	public static void test() {
		ArrayList<int[]> results = new ArrayList<int[]>();
		scan(new File("D:\\minecraft_plugins\\ProxyJoin\\"), results);
		ArrayList<Integer> results_max_stack = new ArrayList<Integer>();
		ArrayList<Integer> results_max_locals = new ArrayList<Integer>();
		for(int[] a : results){
			results_max_stack.add(a[0]);
			results_max_locals.add(a[1]);
		}
		System.out.println("results_max_stack: max: "+max(results_max_stack) + ", avg: " + avg(results_max_stack));
		System.out.println("results_max_locals: max: "+max(results_max_locals) + ", avg: " + avg(results_max_locals));
	}
	
	public static int max(ArrayList<Integer> l){
		int max = 0;
		for(Integer i : l) max = Math.max(max, i.intValue());
		return max;
	}
	public static float avg(ArrayList<Integer> l){
		int cnt = 0;
		for(Integer i : l) cnt += i.intValue();
		return (float)cnt / (float)l.size();
	}

	private static void scan(File dir, ArrayList<int[]> results) {
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				scan(f, results);
			} else {
				try{
					if(f.getName().endsWith(".class")) analyze(f, results);
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	private static void analyze(File dir, ArrayList<int[]> results) {
		try {
			FileInputStream is = new FileInputStream(dir);
			DataInputStream dis = new DataInputStream(new BufferedInputStream(is));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);

			memcpy(dis, dos, 8);// header
			byte[] payload = null;
			int CodeKeywordIndex = -1;
			{// parse pool
				int count = dis.readShort();
				count--;
				dos.writeShort(count);
				for (int i = 0; i < count; i++) {
					int tag = dis.read();
					if (i != count - 1)
						dos.write(tag);
					switch (tag) {
					case 7:// class-title
					case 8:// string
					case 16:// CONSTANT_MethodType: 2 bytes: descriptor_index;
						memcpy(dis, dos, 2);
						break;
					case 3:// int
					case 4:// float
					case 12:// NameAndType: 2 bytes: name_index; 2 bytes:
							// descriptor_index;
					case 18:// InvokeDynamic: 2 bytes:
							// bootstrap_method_attr_index; 2 bytes:
							// name_and_type_index;
					case 9: // CONSTANT_Fieldref: 2 bytes: class_index 2 bytes:
							// name_and_type_index
					case 10:// CONSTANT_Methodref -||-
					case 11:// CONSTANT_InterfaceMethodref -||-
						memcpy(dis, dos, 4);
						break;
					case 5:// long
					case 6:// double
						memcpy(dis, dos, 8);
						i++;
						break;
					case 1:// CONSTANT_Utf8
						payload = new byte[dis.readShort() & 0xFFFF];
						dis.readFully(payload);
						if (new String(payload).equals("Code"))
							CodeKeywordIndex = i + 1;
						if (i != count - 1) {
							dos.writeShort(payload.length);
							dos.write(payload);
						}

						break;
					case 15:// CONSTANT_MethodHandle: 1 byte: reference_kind; 2
							// bytes: reference_index;
						memcpy(dis, dos, 3);
						break;

					default:
						throw new IOException(String.valueOf(tag));
						// System.out.println("tag is unknown! (value="+tag+",
						// counter="+i+")");
						// if(tag == 0) hasReadZeroFromPool = true;
						// i--;
						// continue;
					}
				}
			}
			//DataInputStream payloadProvider = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(payload)));
			{// class configs
				memcpy(dis, dos, 6);
				int ifaces = dis.readShort();
				dos.writeShort(ifaces);
				memcpy(dis, dos, ifaces << 1);
			}
			{// fields
				int fieldCount = dis.readShort();
				dos.writeShort(fieldCount);
				for (int f = 0; f < fieldCount; f++) {
					memcpy(dis, dos, 6);// config
					int attrCount = dis.readShort();
					dos.writeShort(attrCount);
					for (int a = 0; a < attrCount; a++) {
						memcpy(dis, dos, 2);
						int len = dis.readInt();
						dos.writeInt(len);
						memcpy(dis, dos, len);
					}
				}
			}
			{// methods
				int methodCount = dis.readShort();
				dos.writeShort(methodCount);
				for (int m = 0; m < methodCount; m++) {
					memcpy(dis, dos, 6);// config
					int attrCount = dis.readShort();
					dos.writeShort(attrCount);
					for (int a = 0; a < attrCount; a++) {
						int name = dis.readShort();
						dos.writeShort(name);
						int len = dis.readInt();
						byte[] src = new byte[len];
						//System.out.println("len=" + len);
						dis.readFully(src);
						if (name == CodeKeywordIndex) {
							// DataInputStream in = new DataInputStream(new
							// BufferedInputStream(is));
							// ByteArrayOutputStream baos2 = new
							// ByteArrayOutputStream();
							// DataOutputStream out = new
							// DataOutputStream(baos);

							DataInputStream in = new DataInputStream(new ByteArrayInputStream(src));
							int max_stack = in.readShort();
							int max_locals = in.readShort();
							results.add(new int[]{max_stack, max_locals});
						}
						dos.writeInt(src.length);
						dos.write(src);
					}
				}
			}
			{// class attributes
				int attrCount = dis.readShort();
				dos.writeShort(attrCount);
				for (int a = 0; a < attrCount; a++) {
					memcpy(dis, dos, 2);
					int len = dis.readInt();
					dos.writeInt(len);
					memcpy(dis, dos, len);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void memcpy(InputStream is, OutputStream os, int cnt) throws IOException {
		for (int i = 0; i < cnt; i++)
			os.write(is.read());
	}
}
