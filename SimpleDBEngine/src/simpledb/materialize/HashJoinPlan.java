package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.plan.Plan;
import simpledb.query.*;

public class HashJoinPlan implements Plan {
   private Transaction tx;
   private Plan p1, p2;
   private String fldname1, fldname2;
   private Schema sch = new Schema();

   public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.tx = tx;
      this.p1 = p1;
      this.p2 = p2;
      this.fldname1 = fldname1;
      this.fldname2 = fldname2;
      sch.addAll(p1.schema());
      sch.addAll(p2.schema());
   }

   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      int k = partitionCount();
      List<TempTable> parts1 = partition(s1, p1.schema(), fldname1, k);
      List<TempTable> parts2 = partition(s2, p2.schema(), fldname2, k);
      s1.close();
      s2.close();
      return new HashJoinScan(parts1, p1.schema(), fldname1, parts2, p2.schema(), fldname2);
   }

   public int blocksAccessed() {
      return 3 * (p1.blocksAccessed() + p2.blocksAccessed());
   }

   public int recordsOutput() {
      int maxvals = Math.max(1, Math.max(p1.distinctValues(fldname1),
                                         p2.distinctValues(fldname2)));
      long product = (long) p1.recordsOutput() * p2.recordsOutput();
      return (int) Math.min(Integer.MAX_VALUE, product / maxvals);
   }

   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   public Schema schema() {
      return sch;
   }

   private int partitionCount() {
      int avail = tx.availableBuffs();
      int memblocks = Math.max(1, avail - 2);
      int wanted = (int) Math.ceil((double) p2.blocksAccessed() / memblocks);
      return Math.max(1, Math.min(wanted, memblocks));
   }

   private List<TempTable> partition(Scan src, Schema schema, String fldname, int k) {
      List<TempTable> temps = new ArrayList<>();
      List<UpdateScan> dests = new ArrayList<>();
      for (int i = 0; i < k; i++) {
         TempTable temp = new TempTable(tx, schema);
         temps.add(temp);
         dests.add(temp.open());
      }
      src.beforeFirst();
      while (src.next()) {
         UpdateScan dest = dests.get(bucket(src.getVal(fldname), k));
         dest.insert();
         for (String f : schema.fields())
            dest.setVal(f, src.getVal(f));
      }
      for (UpdateScan dest : dests)
         dest.close();
      return temps;
   }

   private static int bucket(Constant val, int k) {
      return (val.hashCode() & Integer.MAX_VALUE) % k;
   }
}
