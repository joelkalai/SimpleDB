package simpledb.test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

import simpledb.opt.JoinStrategy;
import simpledb.opt.JoinStrategy.Algorithm;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Runs the two experiments required for the final report on the
 * big student database (see CreateBigStudentDB):
 * <ol>
 * <li>a 2-table join, timed under each join algorithm;</li>
 * <li>a 4-table join, timed under different execution plans,
 *     produced by restricting which join algorithm the optimizer may use.</li>
 * </ol>
 * Each configuration is run once to warm up and then timed over several
 * runs; the median and minimum are reported, together with the plan
 * the optimizer chose and its estimated block accesses.
 * <P>
 * Usage: <pre>  java simpledb.test.JoinExperiments [dbdir] [runs]</pre>
 */
public class JoinExperiments {
   private static final Algorithm[] ALGORITHMS =
      { Algorithm.INDEX, Algorithm.MERGE, Algorithm.HASH, Algorithm.NESTEDLOOPS, Algorithm.AUTO };

   private static final String TWO_WAY =
      "select sname, grade from student, enroll where sid = studentid";

   private static final String FOUR_WAY =
      "select sname, dname, title, grade from student, enroll, dept, course "
      + "where sid = studentid and majorid = did and did = deptid";

   private static SimpleDB db;
   private static int runs;

   public static void main(String[] args) {
      String dirname = (args.length > 0) ? args[0] : CreateBigStudentDB.DIRNAME;
      runs = (args.length > 1) ? Integer.parseInt(args[1]) : 5;
      db = new SimpleDB(dirname);
      System.out.println("database " + dirname + ", " + SimpleDB.BUFFER_SIZE
            + " buffers, block size " + SimpleDB.BLOCK_SIZE + ", " + runs + " timed runs each");

      experiment("Experiment 1: 2-table join", TWO_WAY);
      experiment("Experiment 2: 4-table join", FOUR_WAY);
      JoinStrategy.force(Algorithm.AUTO);
   }

   private static void experiment(String title, String sql) {
      System.out.println();
      System.out.println("==== " + title);
      System.out.println(sql);
      List<String> summary = new ArrayList<>();
      Integer expectedRows = null;
      for (Algorithm a : ALGORITHMS) {
         JoinStrategy.force(a);
         Transaction tx = db.newTx();
         Plan p = quietly(() -> db.planner().createQueryPlan(sql, tx));
         tx.commit();
         System.out.println();
         System.out.println("-- join algorithms allowed: " + label(a));
         System.out.println(PlanPrinter.format(p));

         run(sql);   // warm-up: JIT compilation and buffer contents
         long[] times = new long[runs];
         int rows = 0;
         for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            rows = run(sql);
            times[i] = System.nanoTime() - start;
         }
         Arrays.sort(times);
         String status = "";
         if (expectedRows == null)
            expectedRows = rows;
         else if (rows != expectedRows)
            status = "  ** row count differs from " + expectedRows + " **";
         String line = String.format("%-24s %6d rows  median %9.2f ms  min %9.2f ms  est. blocks %d%s",
               label(a), rows, times[runs / 2] / 1e6, times[0] / 1e6, p.blocksAccessed(), status);
         System.out.println(line);
         summary.add(line);
      }
      System.out.println();
      System.out.println("Summary (" + title + "):");
      summary.forEach(l -> System.out.println("  " + l));
   }

   /** Plan and fully iterate the query in its own transaction; return the row count. */
   private static int run(String sql) {
      return quietly(() -> {
         Transaction tx = db.newTx();
         Plan p = db.planner().createQueryPlan(sql, tx);
         Scan s = p.open();
         int n = 0;
         while (s.next())
            n++;
         s.close();
         tx.commit();
         return n;
      });
   }

   private static String label(Algorithm a) {
      return (a == Algorithm.AUTO) ? "optimizer's choice" : a.toString().toLowerCase() + " only";
   }

   /** Run the action with System.out silenced, so planner and commit messages are not timed. */
   private static <T> T quietly(java.util.function.Supplier<T> action) {
      PrintStream out = System.out;
      System.setOut(new PrintStream(OutputStream.nullOutputStream()));
      try {
         return action.get();
      }
      finally {
         System.setOut(out);
      }
   }
}
