package simpledb.materialize;

import java.util.Arrays;
import java.util.List;
import simpledb.query.*;

public class DistinctScan implements Scan {
   private Scan s;
   private List<String> fields;
   private Constant[] previous;

   public DistinctScan(Scan s, List<String> fields) {
      this.s = s;
      this.fields = fields;
      beforeFirst();
   }

   public void beforeFirst() {
      s.beforeFirst();
      previous = null;
   }

   public boolean next() {
      while (s.next()) {
         Constant[] current = new Constant[fields.size()];
         for (int i = 0; i < current.length; i++)
            current[i] = s.getVal(fields.get(i));
         if (previous == null || !Arrays.equals(previous, current)) {
            previous = current;
            return true;
         }
      }
      return false;
   }

   public int getInt(String fldname) {
      return s.getInt(fldname);
   }

   public String getString(String fldname) {
      return s.getString(fldname);
   }

   public Constant getVal(String fldname) {
      return s.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return s.hasField(fldname);
   }

   public void close() {
      s.close();
   }
}
