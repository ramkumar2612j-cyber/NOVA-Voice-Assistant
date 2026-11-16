import java.io.*; import java.lang.reflect.*; import java.nio.file.*; import java.util.*;
public class Run {
  public static void main(String[] a) throws Exception {
    Path root = Paths.get(a[0]); List<String> names = new ArrayList<>();
    try (var s = Files.walk(root)) { s.filter(p->p.toString().endsWith("Test.class")).forEach(p->names.add(root.relativize(p).toString().replace('/','.').replaceAll("\\.class$",""))); }
    Collections.sort(names); int pass=0, fail=0;
    for (String n : names) {
      Class<?> c; try { c = Class.forName(n); } catch (Throwable t) { System.out.println("LOADFAIL "+n+" "+t); fail++; continue; }
      for (Method m : c.getDeclaredMethods()) {
        if (m.getAnnotation(org.junit.Test.class)==null) continue;
        Object o = c.getDeclaredConstructor().newInstance();
        try { m.invoke(o); pass++; }
        catch (InvocationTargetException e) { fail++; Throwable t=e.getCause(); System.out.println("FAIL "+n+"."+m.getName()+" -> "+t); StackTraceElement[] st=t.getStackTrace(); for(int i=0;i<Math.min(3,st.length);i++) System.out.println("     at "+st[i]); }
        finally { for (Method x : c.getDeclaredMethods()) if (x.getAnnotation(org.junit.After.class)!=null) { try { x.invoke(o);} catch(Throwable t){} } }
      }
    }
    System.out.println("TESTS pass="+pass+" fail="+fail);
  }
}
