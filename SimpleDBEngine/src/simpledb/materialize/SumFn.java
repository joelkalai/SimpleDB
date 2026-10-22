package simpledb.materialize;

import simpledb.query.*;

public class SumFn implements AggregationFn {
   private String fldname;
   private long sum;

   public SumFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      sum = s.getVal(fldname).asInt();
   }

   public void processNext(Scan s) {
      sum += s.getVal(fldname).asInt();
   }

   public String fieldName() {
      return "sumof" + fldname;
   }

   public Constant value() {
      return new Constant(Math.toIntExact(sum));
   }

   public boolean definedOnEmpty() {
      return true;
   }

   public Constant emptyValue() {
      return new Constant(0);
   }
}
