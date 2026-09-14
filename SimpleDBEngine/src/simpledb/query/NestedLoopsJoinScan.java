package simpledb.query;

/**
 * The scan class corresponding to the <i>nested-loops join</i>
 * relational algebra operator.
 * The code is adapted from {@link ProductScan}: for each LHS record
 * it runs through the entire RHS scan.  The difference is that the
 * join predicate is evaluated here, as part of the inner loop, so
 * that no separate selection operator is needed above the join.
 * @author Edward Sciore
 */
public class NestedLoopsJoinScan implements Scan {
   private Scan s1, s2;
   private Predicate pred;
   private boolean hasmore1;

   /**
    * Create a nested-loops join scan having the two underlying scans.
    * @param s1 the LHS (outer) scan
    * @param s2 the RHS (inner) scan
    * @param pred the join predicate
    */
   public NestedLoopsJoinScan(Scan s1, Scan s2, Predicate pred) {
      this.s1 = s1;
      this.s2 = s2;
      this.pred = pred;
      beforeFirst();
   }

   /**
    * Position the scan before its first record.
    * The LHS scan is positioned at its first record,
    * and the RHS scan is positioned before its first record.
    * Unlike ProductScan, the result of the initial LHS move is
    * remembered, so that an empty LHS is handled correctly.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      s1.beforeFirst();
      hasmore1 = s1.next();
      s2.beforeFirst();
   }

   /**
    * Move to the next record satisfying the join predicate.
    * The method scans the RHS for a matching record; when the RHS is
    * exhausted it moves to the next LHS record and rewinds the RHS.
    * If there are no more LHS records, the method returns false.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (hasmore1) {
         while (s2.next())
            if (pred.isSatisfied(this))
               return true;
         s2.beforeFirst();
         hasmore1 = s1.next();
      }
      return false;
   }

   /**
    * Return the integer value of the specified field.
    * The value is obtained from whichever scan
    * contains the field.
    * @see simpledb.query.Scan#getInt(java.lang.String)
    */
   public int getInt(String fldname) {
      if (s1.hasField(fldname))
         return s1.getInt(fldname);
      else
         return s2.getInt(fldname);
   }

   /**
    * Return the string value of the specified field.
    * The value is obtained from whichever scan
    * contains the field.
    * @see simpledb.query.Scan#getString(java.lang.String)
    */
   public String getString(String fldname) {
      if (s1.hasField(fldname))
         return s1.getString(fldname);
      else
         return s2.getString(fldname);
   }

   /**
    * Return the value of the specified field.
    * The value is obtained from whichever scan
    * contains the field.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      if (s1.hasField(fldname))
         return s1.getVal(fldname);
      else
         return s2.getVal(fldname);
   }

   /**
    * Return true if the specified field is in
    * either of the underlying scans.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || s2.hasField(fldname);
   }

   /**
    * Close both underlying scans.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      s1.close();
      s2.close();
   }
}
