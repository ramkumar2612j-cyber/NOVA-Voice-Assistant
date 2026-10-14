package org.junit;
public class Assert {
    static void f(String m, String d){ throw new AssertionError((m==null?"":m+" ")+d); }
    public static void assertTrue(boolean c){ if(!c) f(null,"expected true"); }
    public static void assertTrue(String m, boolean c){ if(!c) f(m,"expected true"); }
    public static void assertFalse(boolean c){ if(c) f(null,"expected false"); }
    public static void assertFalse(String m, boolean c){ if(c) f(m,"expected false"); }
    public static void assertNull(Object o){ if(o!=null) f(null,"expected null but was <"+o+">"); }
    public static void assertNull(String m, Object o){ if(o!=null) f(m,"expected null but was <"+o+">"); }
    public static void assertNotNull(Object o){ if(o==null) f(null,"expected not null"); }
    public static void assertNotNull(String m, Object o){ if(o==null) f(m,"expected not null"); }
    public static void assertSame(Object a, Object b){ if(a!=b) f(null,"expected same <"+a+"> was <"+b+">"); }
    public static void assertSame(String m, Object a, Object b){ if(a!=b) f(m,"expected same <"+a+"> was <"+b+">"); }
    public static void fail(){ throw new AssertionError(); }
    public static void fail(String m){ throw new AssertionError(m); }
    public static void assertEquals(Object a, Object b){ if(!(a==null?b==null:a.equals(b))) f(null,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertEquals(String m, Object a, Object b){ if(!(a==null?b==null:a.equals(b))) f(m,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertEquals(long a, long b){ if(a!=b) f(null,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertEquals(String m, long a, long b){ if(a!=b) f(m,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertEquals(double a, double b, double d){ if(!(Math.abs(a-b)<=d)) f(null,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertEquals(String m, double a, double b, double d){ if(!(Math.abs(a-b)<=d)) f(m,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertEquals(float a, float b, float d){ if(!(Math.abs(a-b)<=d)) f(null,"expected:<"+a+"> but was:<"+b+">"); }
    public static void assertArrayEquals(byte[] a, byte[] b){ if(!java.util.Arrays.equals(a,b)) f(null,"arrays differ"); }
    public static void assertArrayEquals(Object[] a, Object[] b){ if(!java.util.Arrays.equals(a,b)) f(null,"arrays differ"); }
}
