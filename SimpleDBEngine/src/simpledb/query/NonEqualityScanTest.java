package simpledb.query;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.record.*;

// Checks that <, <=, >, >=, !=, <> (and =) filter rows correctly,
// for both an int field and a string field.
public class NonEqualityScanTest {
   public static void main(String[] args) throws Exception {
      SimpleDB db = new SimpleDB("noneqtest");
      Transaction tx = db.newTx();

      Schema sch = new Schema();
      sch.addIntField("A");
      sch.addStringField("B", 9);
      Layout layout = new Layout(sch);
      UpdateScan s1 = new TableScan(tx, "T", layout);
      s1.beforeFirst();
      for (int i = 0; i < 10; i++) {
         s1.insert();
         s1.setInt("A", i);
         s1.setString("B", "rec" + i);
      }
      s1.close();

      check(tx, layout, new Term(new Expression("A"), "<",  new Expression(new Constant(5))), 5); // 0..4
      check(tx, layout, new Term(new Expression("A"), "<=", new Expression(new Constant(5))), 6); // 0..5
      check(tx, layout, new Term(new Expression("A"), ">",  new Expression(new Constant(5))), 4); // 6..9
      check(tx, layout, new Term(new Expression("A"), ">=", new Expression(new Constant(5))), 5); // 5..9
      check(tx, layout, new Term(new Expression("A"), "!=", new Expression(new Constant(5))), 9); // all but 5
      check(tx, layout, new Term(new Expression("A"), "<>", new Expression(new Constant(5))), 9); // all but 5
      check(tx, layout, new Term(new Expression("A"), "=",  new Expression(new Constant(5))), 1); // just 5

      // string comparisons
      check(tx, layout, new Term(new Expression("B"), "<",  new Expression(new Constant("rec5"))), 5);
      check(tx, layout, new Term(new Expression("B"), "!=", new Expression(new Constant("rec5"))), 9);

      tx.commit();
      System.out.println();
      System.out.println(fail == 0 ? "ALL PASS" : fail + " FAILURES");
   }

   static int fail = 0;

   static void check(Transaction tx, Layout layout, Term t, int expectedCount) throws Exception {
      Predicate pred = new Predicate(t);
      Scan s = new SelectScan(new TableScan(tx, "T", layout), pred);
      s.beforeFirst();
      int count = 0;
      while (s.next()) count++;
      s.close();
      boolean ok = count == expectedCount;
      System.out.println((ok ? "PASS" : "FAIL") + "  " + t + "  -> " + count + " rows (expected " + expectedCount + ")");
      if (!ok) fail++;
   }
}
