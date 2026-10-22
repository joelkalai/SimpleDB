package simpledb.plan;

import java.util.List;
import simpledb.query.Constant;
import simpledb.query.Scan;

public class ExplainScan implements Scan {
   private List<String> lines;
   private int pos = -1;

   public ExplainScan(List<String> lines) {
      this.lines = lines;
   }

   public void beforeFirst() {
      pos = -1;
   }

   public boolean next() {
      pos++;
      return pos < lines.size();
   }

   public int getInt(String fldname) {
      throw new RuntimeException("field " + fldname + " is not an int field.");
   }

   public String getString(String fldname) {
      if (!hasField(fldname))
         throw new RuntimeException("field " + fldname + " not found.");
      return lines.get(pos);
   }

   public Constant getVal(String fldname) {
      return new Constant(getString(fldname));
   }

   public boolean hasField(String fldname) {
      return fldname.equals(ExplainPlan.FIELD);
   }

   public void close() {
   }
}
