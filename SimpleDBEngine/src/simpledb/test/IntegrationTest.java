package simpledb.test;

import java.sql.*;
import java.util.*;
import simpledb.jdbc.embedded.EmbeddedDriver;
import simpledb.opt.JoinStrategy;
import simpledb.opt.JoinStrategy.Algorithm;
import simpledb.plan.BasicQueryPlanner;
import simpledb.plan.BasicUpdatePlanner;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class IntegrationTest {
   private static final String[][] QUERIES = {
      {"equality join", "select sname, dname from student, dept where majorid = did", ""},
      {"join with a selection", "select sname, dname from student, dept where majorid = did and gradyear = 2021", ""},
      {"three-table join", "select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid", ""},
      {"inequality join", "select sname, dname from student, dept where majorid > did", ""},
      {"many-to-many join", "select title, sname from course, student where deptid = majorid", ""},
      {"all five aggregates", "select count(*), sum(gradyear), avg(gradyear), min(gradyear), max(gradyear) from student", ""},
      {"group by", "select majorid, count(*), avg(gradyear) from student group by majorid", ""},
      {"group by two fields", "select majorid, gradyear, count(*) from student group by majorid, gradyear", ""},
      {"aggregate on an index selection", "select count(*) from student where majorid = 20", ""},
      {"aggregate over a join", "select dname, count(sid), max(gradyear) from student, dept where majorid = did group by dname", ""},
      {"aggregate over three tables", "select dname, count(eid) from student, dept, enroll where majorid = did and sid = studentid group by dname", ""},
      {"count over a many-to-many join", "select count(*) from course, student where deptid = majorid", ""},
      {"string min and max over a join", "select dname, min(sname), max(sname) from student, dept where majorid = did group by dname", ""},
      {"group by without an aggregate", "select gradyear from student group by gradyear", ""},
      {"order by an aggregate", "select majorid, count(*) from student group by majorid order by count(*) desc, majorid", "1d,0a"},
      {"order by over a join", "select sname, dname from student, dept where majorid = did order by dname desc, sname", "1d,0a"},
      {"distinct", "select distinct majorid from student", ""},
      {"distinct over a join", "select distinct dname from student, dept where majorid = did", ""},
      {"distinct over three tables", "select distinct dname, grade from student, dept, enroll where majorid = did and sid = studentid", ""},
      {"distinct with order by", "select distinct grade from enroll order by grade", "0a"},
      {"distinct with two order by fields", "select distinct gradyear, majorid from student order by majorid desc, gradyear", "1d,0a"},
      {"distinct over an aggregate", "select distinct count(*) from student group by majorid", ""},
   };

   private static int failures = 0;

   public static void main(String[] args) throws Exception {
      String dirname = (args.length > 0) ? args[0] : "studentdb";
      if (args.length > 1)
         SimpleDB.BUFFER_SIZE = Integer.parseInt(args[1]);
      boolean basic = args.length < 3 || !args[2].equals("nobasic");
      SimpleDB db = new SimpleDB(dirname);
      Planner basicPlanner = new Planner(new BasicQueryPlanner(db.mdMgr()), new BasicUpdatePlanner(db.mdMgr()));

      Algorithm[] algorithms = {Algorithm.AUTO, Algorithm.INDEX, Algorithm.MERGE, Algorithm.HASH,
                                Algorithm.NESTEDLOOPS, Algorithm.PRODUCT};
      Map<String,List<String>> reference = new LinkedHashMap<>();

      for (String[] q : QUERIES) {
         System.out.println();
         System.out.println("--- " + q[0] + ": " + q[1]);
         List<String> expected = null;
         String expectedFrom = null;
         if (basic) {
            expected = rows(db, basicPlanner, q[1], q[2], "basic planner");
            expectedFrom = "basic planner";
         }
         for (Algorithm alg : algorithms) {
            JoinStrategy.force(alg);
            List<String> actual = rows(db, db.planner(), q[1], q[2], alg.toString().toLowerCase());
            if (actual == null)
               continue;
            if (expected == null) {
               expected = actual;
               expectedFrom = alg.toString().toLowerCase();
            }
            else if (!sorted(actual).equals(sorted(expected))) {
               System.out.println("  FAIL  " + alg.toString().toLowerCase() + " disagrees with " + expectedFrom
                     + ": " + describe(actual) + " vs " + describe(expected));
               failures++;
            }
         }
         JoinStrategy.force(Algorithm.AUTO);
         if (expected != null) {
            System.out.println("  " + (basic ? "basic planner and " : "") + "all join algorithms agree: "
                  + expected.size() + " rows " + describe(expected));
            reference.put(q[1], expected);
         }
      }

      System.out.println();
      System.out.println("=== JDBC (embedded driver)");
      checkJdbc(dirname, reference);

      System.out.println();
      System.out.println(failures == 0 ? "Integration checks passed." : failures + " check(s) failed.");
   }

   private static void checkJdbc(String dirname, Map<String,List<String>> reference) throws SQLException {
      Driver d = new EmbeddedDriver();
      try (Connection conn = d.connect("jdbc:simpledb:" + dirname, null);
           Statement stmt = conn.createStatement()) {
         for (String[] q : QUERIES) {
            List<String> expected = reference.get(q[1]);
            if (expected == null)
               continue;
            List<String> actual = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(q[1])) {
               ResultSetMetaData md = rs.getMetaData();
               while (rs.next()) {
                  StringBuilder sb = new StringBuilder();
                  for (int i = 1; i <= md.getColumnCount(); i++) {
                     if (i > 1)
                        sb.append("|");
                     String name = md.getColumnName(i);
                     sb.append(md.getColumnType(i) == Types.INTEGER ? String.valueOf(rs.getInt(name)) : rs.getString(name));
                  }
                  actual.add(sb.toString());
               }
            }
            boolean ok = sorted(actual).equals(sorted(expected));
            System.out.println((ok ? "  PASS  " : "  FAIL  ") + q[0]);
            if (!ok)
               failures++;
            checkExplain(stmt, q);
         }
      }
   }

   private static void checkExplain(Statement stmt, String[] q) throws SQLException {
      List<String> lines = new ArrayList<>();
      try (ResultSet rs = stmt.executeQuery("explain " + q[1])) {
         while (rs.next())
            lines.add(rs.getString("queryplan"));
      }
      boolean ok = !lines.isEmpty() && (lines.get(0).startsWith("ProjectPlan") || lines.get(0).startsWith("DistinctPlan"));
      if (!ok) {
         System.out.println("  FAIL  explain " + q[0] + ": " + lines);
         failures++;
      }
   }

   private static List<String> rows(SimpleDB db, Planner planner, String sql, String order, String label) {
      Transaction tx = db.newTx();
      try {
         Plan p = planner.createQueryPlan(sql, tx);
         List<String> fields = p.schema().fields();
         List<String> out = new ArrayList<>();
         Scan s = p.open();
         while (s.next()) {
            StringBuilder sb = new StringBuilder();
            for (String f : fields) {
               if (sb.length() > 0)
                  sb.append("|");
               sb.append(s.getVal(f));
            }
            out.add(sb.toString());
         }
         s.close();
         tx.commit();
         if (!order.isEmpty() && !ordered(out, order)) {
            System.out.println("  FAIL  " + label + " does not honour order by " + order + ": " + describe(out));
            failures++;
         }
         return out;
      }
      catch (RuntimeException e) {
         tx.rollback();
         System.out.println("  FAIL  " + label + " threw " + e);
         failures++;
         return null;
      }
   }

   private static boolean ordered(List<String> rows, String order) {
      String[] keys = order.split(",");
      for (int i = 1; i < rows.size(); i++) {
         String[] a = rows.get(i - 1).split("\\|");
         String[] b = rows.get(i).split("\\|");
         for (String key : keys) {
            int col = Integer.parseInt(key.substring(0, key.length() - 1));
            boolean asc = key.endsWith("a");
            int cmp = compare(a[col], b[col]);
            if (cmp == 0)
               continue;
            if (asc ? cmp > 0 : cmp < 0)
               return false;
            break;
         }
      }
      return true;
   }

   private static int compare(String x, String y) {
      try {
         return Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
      }
      catch (NumberFormatException e) {
         return x.compareTo(y);
      }
   }

   private static List<String> sorted(List<String> rows) {
      List<String> copy = new ArrayList<>(rows);
      Collections.sort(copy);
      return copy;
   }

   private static String describe(List<String> rows) {
      return rows.size() <= 4 ? rows.toString() : rows.subList(0, 4) + "...";
   }
}
