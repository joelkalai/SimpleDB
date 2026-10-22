package simpledb.materialize;

import java.util.Arrays;
import java.util.List;
import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;

public class DistinctPlan implements Plan {
   private Plan src;
   private Plan p;
   private List<SortField> sortfields;

   public DistinctPlan(Transaction tx, Plan src, List<SortField> sortfields) {
      this.src = src;
      this.sortfields = sortfields;
      this.p = new SortPlan(tx, src, sortfields);
   }

   public Scan open() {
      Scan s = p.open();
      return new DistinctScan(s, p.schema().fields());
   }

   public int blocksAccessed() {
      return p.blocksAccessed();
   }

   public int recordsOutput() {
      long bound = 1;
      for (String fldname : src.schema().fields()) {
         bound *= Math.max(1, src.distinctValues(fldname));
         if (bound >= src.recordsOutput())
            return src.recordsOutput();
      }
      return (int) bound;
   }

   public int distinctValues(String fldname) {
      return Math.min(src.distinctValues(fldname), recordsOutput());
   }

   public Schema schema() {
      return src.schema();
   }

   public String describe() {
      return "DistinctPlan on " + src.schema().fields() + " ordered by " + sortfields;
   }

   public List<Plan> children() {
      return Arrays.asList(p);
   }
}
