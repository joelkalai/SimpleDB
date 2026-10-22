package simpledb.test;

import java.util.*;
import simpledb.opt.JoinStrategy;
import simpledb.opt.JoinStrategy.Algorithm;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class ExplainTest {
   private static final String[][] QUERIES = {
      {"table scan", "select sname from student"},
      {"index select", "select sname from student where majorid = 20"},
      {"join", "select sname, dname from student, dept where majorid = did"},
      {"three-way join", "select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid"},
      {"order by", "select sname from student order by gradyear desc, sname"},
      {"group by", "select majorid, count(*), avg(gradyear) from student group by majorid order by majorid"},
      {"aggregate over a join", "select dname, count(sid) from student, dept where majorid = did group by dname"},
      {"distinct", "select distinct gradyear from student order by gradyear"},
      {"inequality join", "select sname, dname from student, dept where majorid > did"},
   };

   public static void main(String[] args) {
      String dirname = (args.length > 0) ? args[0] : "studentdb";
      if (args.length > 1)
         SimpleDB.BUFFER_SIZE = Integer.parseInt(args[1]);
      SimpleDB db = new SimpleDB(dirname);
      int failures = 0;

      for (String[] q : QUERIES) {
         System.out.println();
         System.out.println("=== " + q[0] + ": " + q[1]);
         List<String> lines = explain(db, q[1]);
         for (String line : lines)
            System.out.println(line);
         if (lines.isEmpty() || !lines.get(0).startsWith("ProjectPlan") && !lines.get(0).startsWith("DistinctPlan")) {
            System.out.println("*** the root of the plan is not a project or distinct");
            failures++;
         }
      }

      System.out.println();
      System.out.println("=== forced algorithms: explain select sname, dname from student, dept where majorid = did");
      for (Algorithm alg : new Algorithm[] {Algorithm.MERGE, Algorithm.HASH, Algorithm.NESTEDLOOPS, Algorithm.INDEX}) {
         JoinStrategy.force(alg);
         List<String> lines = explain(db, "select sname, dname from student, dept where majorid = did");
         String expected = alg == Algorithm.MERGE ? "MergeJoinPlan"
                         : alg == Algorithm.HASH ? "HashJoinPlan"
                         : alg == Algorithm.NESTEDLOOPS ? "NestedLoopsJoinPlan" : "IndexJoinPlan";
         boolean found = false;
         for (String line : lines)
            if (line.contains(expected))
               found = true;
         System.out.println((found ? "PASS  " : "FAIL  ") + alg + " -> " + expected);
         if (!found) {
            for (String line : lines)
               System.out.println("      " + line);
            failures++;
         }
      }
      JoinStrategy.force(Algorithm.AUTO);

      System.out.println();
      System.out.println(failures == 0 ? "Explain checks passed." : failures + " check(s) failed.");
   }

   private static List<String> explain(SimpleDB db, String sql) {
      Transaction tx = db.newTx();
      Plan p = db.planner().createQueryPlan("explain " + sql, tx);
      List<String> lines = new ArrayList<>();
      Scan s = p.open();
      while (s.next())
         lines.add(s.getString("queryplan"));
      s.close();
      tx.commit();
      return lines;
   }
}
