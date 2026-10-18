package simpledb.plan;

import simpledb.query.NestedLoopsJoinScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/** The Plan class corresponding to the <i>nested-loops join</i>
  * relational algebra operator.
  * <P>
  * The class is adapted from {@link ProductPlan}.  The difference is
  * that it carries the join predicate, which the scan applies while
  * looping; there is therefore no selection operator above the join.
  * @author Edward Sciore
  */
public class NestedLoopsJoinPlan implements Plan {
   private Plan p1, p2;
   private Predicate joinpred;
   private Schema schema = new Schema();

   /**
    * Creates a new nested-loops join node in the query tree,
    * having the two specified subqueries.
    * @param p1 the left-hand (outer) subquery
    * @param p2 the right-hand (inner) subquery
    * @param joinpred the predicate relating the two subqueries
    */
   public NestedLoopsJoinPlan(Plan p1, Plan p2, Predicate joinpred) {
      this.p1 = p1;
      this.p2 = p2;
      this.joinpred = joinpred;
      schema.addAll(p1.schema());
      schema.addAll(p2.schema());
   }

   /**
    * Creates a nested-loops join scan for this query.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopsJoinScan(s1, s2, joinpred);
   }

   /**
    * Estimates the number of block accesses in the join.
    * The inner relation is scanned once per outer record, so the
    * formula is the same as for a product:
    * <pre> B(nestedloops(p1,p2)) = B(p1) + R(p1)*B(p2) </pre>
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return p1.blocksAccessed() + (p1.recordsOutput() * p2.blocksAccessed());
   }

   /**
    * Estimates the number of output records in the join.
    * The formula is that of a product reduced by the join predicate:
    * <pre> R(nestedloops(p1,p2)) = R(p1)*R(p2)/V(joinpred) </pre>
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      int product = p1.recordsOutput() * p2.recordsOutput();
      if (joinpred == null)
         return product;
      int factor = joinpred.reductionFactor(this);
      return (factor == 0) ? product : product / factor;
   }

   /**
    * Estimates the distinct number of field values in the join.
    * Since the join does not increase or decrease field values,
    * the estimate is the same as in the appropriate underlying query.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   /**
    * Returns the schema of the join,
    * which is the union of the schemas of the underlying queries.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return schema;
   }
}
