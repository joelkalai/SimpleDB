package simpledb.test;

import java.util.*;
import simpledb.opt.JoinStrategy;
import simpledb.opt.JoinStrategy.Algorithm;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Exercises the three join algorithms against the student database.
 * <P>
 * Each query is planned and run once per algorithm.  The plan the
 * optimizer keeps is printed, together with the number of rows and the
 * rows themselves, and every forced algorithm's result is compared
 * against the result the optimizer chose on its own.  Since the join
 * algorithms emit rows in different orders, the comparison is on the
 * sorted rows.
 * <P>
 * Run CreateStudentDB first.  A single algorithm can be forced for the
 * whole run with -Dsimpledb.join=mergejoin, in which case only that
 * algorithm is exercised.
 * <P>
 * Usage: JoinTest [dbdirectory [numbuffers]].  The larger database built
 * by CreateBigStudentDB needs more than the eight default buffers.
 */
public class JoinTest {
   private static final String[][] QUERIES = {
      {"two tables, equality join",
       "select sname, dname from student, dept where majorid = did"},
      {"two tables, join on an indexed field",
       "select sname, grade from student, enroll where sid = studentid"},
      {"three tables",
       "select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid"},
      {"join plus a single-table selection",
       "select sname, dname from student, dept where majorid = did and gradyear = 2021"},
      {"many-to-many join (duplicates on both sides)",
       "select title, sname from course, student where deptid = majorid"},
      {"inequality join: only nested loops applies",
       "select sname, dname from student, dept where majorid > did"},
      {"no join predicate: cross product",
       "select sname, dname from student, dept"},
   };

   public static void main(String[] args) {
      String dirname = (args.length > 0) ? args[0] : "studentdb";
      if (args.length > 1)
         SimpleDB.BUFFER_SIZE = Integer.parseInt(args[1]);
      try {
         SimpleDB db = new SimpleDB(dirname);

         List<Algorithm> algorithms = new ArrayList<>();
         if (JoinStrategy.current() != Algorithm.AUTO)
            algorithms.add(JoinStrategy.current());
         else
            algorithms.addAll(Arrays.asList(Algorithm.AUTO, Algorithm.INDEX,
                                            Algorithm.MERGE, Algorithm.HASH,
                                            Algorithm.NESTEDLOOPS,
                                            Algorithm.PRODUCT));

         int failures = 0;
         for (String[] q : QUERIES) {
            System.out.println();
            System.out.println("=======================================================");
            System.out.println(q[0]);
            System.out.println("  " + q[1]);
            System.out.println("=======================================================");

            List<String> expected = null;
            for (Algorithm alg : algorithms) {
               JoinStrategy.force(alg);
               System.out.println();
               System.out.println("-- " + alg.toString().toLowerCase() + " --");
               List<String> rows = run(db, q[1]);
               Collections.sort(rows);
               System.out.println("  " + rows.size() + " rows"
                     + (rows.size() <= 30 ? ": " + rows : " (first 5: " + rows.subList(0, 5) + ")"));
               if (expected == null)
                  expected = rows;
               else if (!expected.equals(rows)) {
                  System.out.println("  *** MISMATCH against the first run ***");
                  failures++;
               }
            }
         }
         JoinStrategy.force(Algorithm.AUTO);

         System.out.println();
         System.out.println(failures == 0
               ? "All algorithms agreed on every query."
               : failures + " mismatch(es) found.");
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   /** Plan and run one query, returning its rows as strings. */
   private static List<String> run(SimpleDB db, String qry) {
      Transaction tx = db.newTx();
      List<String> rows = new ArrayList<>();
      Plan p = db.planner().createQueryPlan(qry, tx);
      List<String> fields = p.schema().fields();
      Scan s = p.open();
      while (s.next()) {
         StringBuilder sb = new StringBuilder();
         for (String fld : fields) {
            if (sb.length() > 0)
               sb.append("|");
            sb.append(s.getVal(fld));
         }
         rows.add(sb.toString());
      }
      s.close();
      tx.commit();
      return rows;
   }
}
