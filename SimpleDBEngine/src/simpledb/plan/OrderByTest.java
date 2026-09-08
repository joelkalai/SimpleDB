package simpledb.plan;

import java.util.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.*;

/**
 * Tests the order by clause against the studentdb database.
 * Each case runs a query and checks the rows come back in the
 * expected order.  Run CreateStudentDB first.
 */
public class OrderByTest {
   private static int failures = 0;

   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("studentdb");
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      // ascending is the default when no direction is given
      check(planner, tx, "single field, default direction",
            "select sname from student order by sname",
            "sname",
            "amy art bob joe kim lee max pat sue");

      check(planner, tx, "single field, explicit asc",
            "select sname from student order by sname asc",
            "sname",
            "amy art bob joe kim lee max pat sue");

      check(planner, tx, "single field, desc",
            "select sname from student order by sname desc",
            "sname",
            "sue pat max lee kim joe bob art amy");

      // an integer field, to show sorting is not string-only
      check(planner, tx, "int field, desc",
            "select sid from student order by sid desc",
            "sid",
            "9 8 7 6 5 4 3 2 1");

      // the mixed-direction case from the assignment brief
      check(planner, tx, "two fields, mixed directions",
            "select sid, sname, gradyear from student order by gradyear asc, sname desc",
            "sname",
            "pat kim bob amy lee joe art sue max");

      // both fields ascending, to contrast with the case above
      check(planner, tx, "two fields, both ascending",
            "select sid, sname, gradyear from student order by gradyear, sname",
            "sname",
            "pat amy bob kim art joe lee max sue");

      // order by combined with a where clause
      check(planner, tx, "order by with where",
            "select sname, gradyear from student where gradyear > 2020 order by sname desc",
            "sname",
            "sue max lee joe art");

      // ordering on a field that is not in the select list
      check(planner, tx, "order by a non-selected field",
            "select sname from student order by sid desc",
            "sname",
            "lee pat art kim bob sue max amy joe");

      // no order by clause: no SortPlan node, table order preserved
      check(planner, tx, "no order by clause",
            "select sname from student",
            "sname",
            "joe amy max sue bob kim art pat lee");

      tx.commit();
      System.out.println();
      System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILURE(S)");
   }

   private static void check(Planner planner, Transaction tx, String label,
                             String qry, String fldname, String expected) {
      Plan p = planner.createQueryPlan(qry, tx);
      Scan s = p.open();
      StringBuilder sb = new StringBuilder();
      while (s.next()) {
         if (sb.length() > 0) sb.append(" ");
         sb.append(s.getVal(fldname).toString());
      }
      s.close();
      String actual = sb.toString();
      if (actual.equals(expected))
         System.out.println("PASS  " + label);
      else {
         failures++;
         System.out.println("FAIL  " + label);
         System.out.println("        query    " + qry);
         System.out.println("        expected " + expected);
         System.out.println("        actual   " + actual);
      }
   }
}
