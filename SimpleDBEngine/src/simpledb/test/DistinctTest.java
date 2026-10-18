package simpledb.test;

import java.util.*;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class DistinctTest {
   private static SimpleDB db;
   private static int failures = 0;

   public static void main(String[] args) {
      String dirname = (args.length > 0) ? args[0] : "studentdb";
      if (args.length > 1)
         SimpleDB.BUFFER_SIZE = Integer.parseInt(args[1]);
      db = new SimpleDB(dirname);

      List<List<String>> students = run("select sid, majorid, gradyear from student");

      check("distinct one field", "select distinct majorid from student",
            distinctOf(students, 1), false);
      check("distinct two fields", "select distinct majorid, gradyear from student",
            distinctOf(students, 1, 2), false);
      check("distinct matches group by", "select distinct majorid from student",
            run("select majorid from student group by majorid"), false);
      check("distinct on a key keeps every row", "select distinct sid from student",
            distinctOf(students, 0), false);
      check("without distinct the duplicates remain", "select majorid from student",
            projected(students, 1), false);

      List<List<String>> asc = run("select distinct gradyear from student order by gradyear");
      List<List<String>> sorted = new ArrayList<>(asc);
      sorted.sort(Comparator.comparingInt(r -> Integer.parseInt(r.get(0))));
      report("distinct with order by asc", sameSet(asc, distinctOf(students, 2)) && asc.equals(sorted), asc);

      List<List<String>> desc = run("select distinct gradyear from student order by gradyear desc");
      List<List<String>> reversed = new ArrayList<>(sorted);
      Collections.reverse(reversed);
      report("distinct with order by desc", desc.equals(reversed), desc);

      check("distinct with where", "select distinct gradyear from student where majorid = 20",
            distinctOfWhere(students, 1, "20", 2), false);
      check("distinct over a join", "select distinct dname from student, dept where majorid = did",
            distinctJoin(students), false);
      check("distinct with aggregate", "select distinct count(*) from student group by majorid",
            distinctCounts(students), false);
      check("distinct on empty input", "select distinct majorid from student where sid = 99999",
            new ArrayList<List<String>>(), false);

      expectError("order by outside the select list", "select distinct majorid from student order by gradyear");

      System.out.println();
      System.out.println(failures == 0 ? "All distinct checks passed." : failures + " check(s) failed.");
   }

   private static List<List<String>> projected(List<List<String>> rows, int... cols) {
      List<List<String>> out = new ArrayList<>();
      for (List<String> r : rows) {
         List<String> p = new ArrayList<>();
         for (int c : cols)
            p.add(r.get(c));
         out.add(p);
      }
      return out;
   }

   private static List<List<String>> distinctOf(List<List<String>> rows, int... cols) {
      return new ArrayList<>(new LinkedHashSet<>(projected(rows, cols)));
   }

   private static List<List<String>> distinctOfWhere(List<List<String>> rows, int wherecol, String val, int col) {
      List<List<String>> out = new ArrayList<>();
      for (List<String> r : rows)
         if (r.get(wherecol).equals(val))
            out.add(Arrays.asList(r.get(col)));
      return new ArrayList<>(new LinkedHashSet<>(out));
   }

   private static List<List<String>> distinctJoin(List<List<String>> students) {
      Map<String,String> names = new HashMap<>();
      for (List<String> d : run("select did, dname from dept"))
         names.put(d.get(0), d.get(1));
      List<List<String>> out = new ArrayList<>();
      for (List<String> r : students)
         if (names.containsKey(r.get(1)))
            out.add(Arrays.asList(names.get(r.get(1))));
      return new ArrayList<>(new LinkedHashSet<>(out));
   }

   private static List<List<String>> distinctCounts(List<List<String>> students) {
      Map<String,Integer> counts = new HashMap<>();
      for (List<String> r : students)
         counts.merge(r.get(1), 1, Integer::sum);
      List<List<String>> out = new ArrayList<>();
      for (int c : counts.values())
         out.add(Arrays.asList(String.valueOf(c)));
      return new ArrayList<>(new LinkedHashSet<>(out));
   }

   private static boolean sameSet(List<List<String>> a, List<List<String>> b) {
      return new HashSet<>(a).equals(new HashSet<>(b)) && a.size() == b.size();
   }

   private static void check(String title, String sql, List<List<String>> expected, boolean ordered) {
      List<List<String>> actual = run(sql);
      boolean ok;
      if (ordered)
         ok = actual.equals(expected);
      else {
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
