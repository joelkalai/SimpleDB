package simpledb.index;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import simpledb.tx.Transaction;
import simpledb.metadata.IndexInfo;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;

// Proves that (1) a hash index and a btree index can coexist on the same
// table and both resolve equality lookups correctly, and (2) a
// non-equality predicate on an indexed field is NOT accelerated by the
// index (per Term.equatesWithConstant()'s "=" gating), yet still returns
// the correct rows via SelectScan.
public class IndexTypeTest {
   static int fail = 0;

   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("indextypetest");
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      planner.executeUpdate("create table t(a int, b int)", tx);
      planner.executeUpdate("create index idx_a on t(a) using hash", tx);
      planner.executeUpdate("create index idx_b on t(b) using btree", tx);
      for (int i = 0; i < 20; i++)
         planner.executeUpdate("insert into t(a, b) values(" + i + ", " + i + ")", tx);

      MetadataMgr mdm = db.mdMgr();
      Map<String,IndexInfo> idx = mdm.getIndexInfo("t", tx);
      check("idx_a reports type hash", "hash".equals(idx.get("a").indexType()));
      check("idx_b reports type btree", "btree".equals(idx.get("b").indexType()));

      // Equality on the hash-indexed field: index path used, correct row.
      String out1 = captureStdout(planner, tx, "select b from t where a = 5");
      check("equality on a uses the hash index", out1.contains("index on a used"));
      checkRowCount(planner, tx, "select b from t where a = 5", 1);

      // Equality on the btree-indexed field: index path used, correct row.
      String out2 = captureStdout(planner, tx, "select a from t where b = 7");
      check("equality on b uses the btree index", out2.contains("index on b used"));
      checkRowCount(planner, tx, "select a from t where b = 7", 1);

      // Non-equality on the SAME indexed field a: must NOT use the index,
      // but must still return the right rows.
      String out3 = captureStdout(planner, tx, "select b from t where a < 5");
      check("range predicate on a does NOT use the index", !out3.contains("index on a used"));
      checkRowCount(planner, tx, "select b from t where a < 5", 5); // a = 0..4

      tx.commit();
      System.out.println();
      System.out.println(fail == 0 ? "ALL PASS" : fail + " FAILURES");
   }

   private static void check(String label, boolean ok) {
      System.out.println((ok ? "PASS" : "FAIL") + "  " + label);
      if (!ok) fail++;
   }

   private static void checkRowCount(Planner planner, Transaction tx, String qry, int expected) {
      Plan p = planner.createQueryPlan(qry, tx);
      Scan s = p.open();
      int count = 0;
      while (s.next()) count++;
      s.close();
      check(qry + " -> " + count + " rows (expected " + expected + ")", count == expected);
   }

   private static String captureStdout(Planner planner, Transaction tx, String qry) {
      PrintStream old = System.out;
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(buf));
      try {
         planner.createQueryPlan(qry, tx);
      } finally {
         System.setOut(old);
      }
      String captured = buf.toString();
      System.out.print(captured); // still surface it on the real console
      return captured;
   }
}
