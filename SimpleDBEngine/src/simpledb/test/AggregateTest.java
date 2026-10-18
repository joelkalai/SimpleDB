package simpledb.test;

import java.util.*;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class AggregateTest {
   private static SimpleDB db;
   private static int failures = 0;

   public static void main(String[] args) {
      String dirname = (args.length > 0) ? args[0] : "studentdb";
      if (args.length > 1)
         SimpleDB.BUFFER_SIZE = Integer.parseInt(args[1]);
      db = new SimpleDB(dirname);

      List<List<String>> students = run("select sid, sname, majorid, gradyear from student");

      long sumGrad = 0, minGrad = Long.MAX_VALUE, maxGrad = Long.MIN_VALUE;
      String minName = null, maxName = null;
      for (List<String> r : students) {
         long g = Long.parseLong(r.get(3));
         sumGrad += g;
         minGrad = Math.min(minGrad, g);
         maxGrad = Math.max(maxGrad, g);
         if (minName == null || r.get(1).compareTo(minName) < 0)
            minName = r.get(1);
         if (maxName == null || r.get(1).compareTo(maxName) > 0)
            maxName = r.get(1);
      }
      int n = students.size();

      check("count(*)", "select count(*) from student", rows(row(n)));
      check("count(field)", "select count(sid) from student", rows(row(n)));
      check("sum", "select sum(gradyear) from student", rows(row(sumGrad)));
      check("avg", "select avg(gradyear) from student", rows(row(sumGrad / n)));
      check("min and max", "select min(gradyear), max(gradyear) from student",
            rows(row(minGrad, maxGrad)));
      check("all five", "select count(sid), sum(gradyear), avg(gradyear), min(gradyear), max(gradyear) from student",
            rows(row(n, sumGrad, sumGrad / n, minGrad, maxGrad)));
      check("min and max of a string field", "select min(sname), max(sname) from student",
            rows(row(minName, maxName)));

      Map<String,long[]> bymajor = new TreeMap<>();
      for (List<String> r : students) {
         long[] a = bymajor.computeIfAbsent(r.get(2), k -> new long[] {0, 0, Long.MAX_VALUE, Long.MIN_VALUE});
         long g = Long.parseLong(r.get(3));
         a[0]++;
         a[1] += g;
         a[2] = Math.min(a[2], g);
         a[3] = Math.max(a[3], g);
      }
      List<List<String>> expected = new ArrayList<>();
      for (Map.Entry<String,long[]> e : bymajor.entrySet()) {
         long[] a = e.getValue();
         expected.add(row(e.getKey(), a[0], a[1], a[1] / a[0], a[2], a[3]));
      }
      check("group by one field",
            "select majorid, count(sid), sum(gradyear), avg(gradyear), min(gradyear), max(gradyear) from student group by majorid",
            expected);

      Map<String,Integer> bymajoryear = new TreeMap<>();
      for (List<String> r : students)
         bymajoryear.merge(r.get(2) + "|" + r.get(3), 1, Integer::sum);
      expected = new ArrayList<>();
      for (Map.Entry<String,Integer> e : bymajoryear.entrySet()) {
         String[] k = e.getKey().split("\\|");
         expected.add(row(k[0], k[1], e.getValue()));
      }
      check("group by two fields", "select majorid, gradyear, count(*) from student group by majorid, gradyear",
            expected);

      expected = new ArrayList<>();
      for (String m : bymajor.keySet())
         expected.add(row(m));
      check("group by without an aggregate", "select majorid from student group by majorid", expected);

      check("where clause", "select count(*), max(gradyear) from student where majorid = 10",
            filtered(students, 2, "10"));
      check("empty input, count and sum", "select count(sid), sum(gradyear) from student where sid = 99999",
            rows(row(0, 0)));
      check("empty input, grouped", "select majorid, count(*) from student where sid = 99999 group by majorid",
            new ArrayList<List<String>>());
      check("empty input, min", "select min(gradyear) from student where sid = 99999",
            new ArrayList<List<String>>());

      List<List<String>> depts = run("select did, dname from dept");
      Map<String,String> deptname = new HashMap<>();
      for (List<String> d : depts)
         deptname.put(d.get(0), d.get(1));
      Map<String,Integer> bydept = new TreeMap<>();
      for (List<String> r : students)
         if (deptname.containsKey(r.get(2)))
            bydept.merge(deptname.get(r.get(2)), 1, Integer::sum);
      expected = new ArrayList<>();
      for (Map.Entry<String,Integer> e : bydept.entrySet())
         expected.add(row(e.getKey(), e.getValue()));
      check("aggregate over a join", "select dname, count(sid) from student, dept where majorid = did group by dname",
            expected);

      List<List<String>> ordered = run("select majorid, count(*) from student group by majorid order by count(*) desc, majorid");
      boolean sorted = true;
      for (int i = 1; i < ordered.size(); i++)
         if (Integer.parseInt(ordered.get(i - 1).get(1)) < Integer.parseInt(ordered.get(i).get(1)))
            sorted = false;
      report("order by an aggregate", sorted && ordered.size() == bymajor.size(), ordered);

      expectError("non-grouped field", "select sname, count(*) from student");
      expectError("sum of a string", "select sum(sname) from student");
      expectError("avg of a string", "select avg(sname) from student");
      expectError("star in sum", "select sum(*) from student");
      expectError("unknown field", "select sum(nosuchfield) from student");

      System.out.println();
      System.out.println(failures == 0 ? "All aggregate checks passed." : failures + " check(s) failed.");
   }

   private static List<List<String>> filtered(List<List<String>> students, int col, String val) {
      long count = 0, max = Long.MIN_VALUE;
      for (List<String> r : students)
         if (r.get(col).equals(val)) {
            count++;
            max = Math.max(max, Long.parseLong(r.get(3)));
         }
      return count == 0 ? new ArrayList<List<String>>() : rows(row(count, max));
   }

   private static List<String> row(Object... vals) {
      List<String> r = new ArrayList<>();
      for (Object v : vals)
         r.add(String.valueOf(v));
      return r;
   }

   @SafeVarargs
   private static List<List<String>> rows(List<String>... rs) {
      return new ArrayList<>(Arrays.asList(rs));
   }

   private static void check(String title, String sql, List<List<String>> expected) {
      List<List<String>> actual = run(sql);
      boolean ok = actual.equals(expected);
      if (!ok) {
         List<List<String>> a = new ArrayList<>(actual), e = new ArrayList<>(expected);
         Comparator<List<String>> cmp = Comparator.comparing(Object::toString);
         a.sort(cmp);
         e.sort(cmp);
         ok = a.equals(e);
      }
      report(title, ok, actual);
      if (!ok)
         System.out.println("    expected " + expected);
   }

   private static void report(String title, boolean ok, List<List<String>> actual) {
      System.out.println((ok ? "PASS  " : "FAIL  ") + title);
      System.out.println("      " + (actual.size() <= 8 ? actual : actual.subList(0, 8) + " ... (" + actual.size() + " rows)"));
      if (!ok)
         failures++;
   }

   private static void expectError(String title, String sql) {
      Transaction tx = db.newTx();
      try {
         db.planner().createQueryPlan(sql, tx).open().close();
         System.out.println("FAIL  " + title + " (no error)");
         failures++;
      }
      catch (RuntimeException e) {
         System.out.println("PASS  " + title + " -> " + e.getMessage());
      }
      finally {
         tx.rollback();
      }
   }

   private static List<List<String>> run(String sql) {
      Transaction tx = db.newTx();
      Plan p = db.planner().createQueryPlan(sql, tx);
      List<String> fields = p.schema().fields();
      List<List<String>> out = new ArrayList<>();
      Scan s = p.open();
      while (s.next()) {
         List<String> r = new ArrayList<>();
         for (String f : fields)
            r.add(s.getVal(f).toString());
         out.add(r);
      }
      s.close();
      tx.commit();
      return out;
   }
}
