package simpledb.opt;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.*;
import simpledb.metadata.*;
import simpledb.index.planner.*;
import simpledb.materialize.MergeJoinPlan;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.opt.JoinStrategy.Algorithm;
import simpledb.plan.*;

/**
 * This class contains methods for planning a single table.
 * @author Edward Sciore
 */
class TablePlanner {
   private String tblname;
   private TablePlan myplan;
   private Predicate mypred;
   private Schema myschema;
   private Map<String,IndexInfo> indexes;
   private Transaction tx;
   private String selectchoice;   // how the most recent makeSelectPlan accessed the table
   private String joinchoice;     // how the most recent makeJoinPlan joined the table
   private String indexjoindesc;  // the index picked by the most recent makeIndexJoin

   /**
    * Creates a new table planner.
    * The specified predicate applies to the entire query.
    * The table planner is responsible for determining
    * which portion of the predicate is useful to the table,
    * and when indexes are useful.
    * @param tblname the name of the table
    * @param mypred the query predicate
    * @param tx the calling transaction
    */
   public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
      this.tblname = tblname;
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = new TablePlan(tx, tblname, mdm);
      myschema = myplan.schema();
      indexes  = mdm.getIndexInfo(tblname, tx);
   }

   /**
    * Constructs a select plan for the table.
    * The plan will use an indexselect, if possible.
    * @return a select plan for the table.
    */
   public Plan makeSelectPlan() {
      Plan p = makeIndexSelect();
      if (p == null) {
         selectchoice = tblname + ": tablescan";
         p = myplan;
      }
      return addSelectPred(p);
   }

   /**
    * Constructs a join plan of the specified plan
    * and the table.  Every join algorithm that applies is planned,
    * and the one with the lowest estimated block accesses is kept:
    * an indexjoin when this table has an index on a join field, a
    * mergejoin when the tables are related by an equality term, and a
    * nested-loops join, which always applies.
    * The choice can be overridden with {@link JoinStrategy}.
    * The method returns null if no join is possible.
    * @param current the specified plan
    * @return a join plan of the plan and this table
    */
   public Plan makeJoinPlan(Plan current) {
      Schema currsch = current.schema();
      if (mypred.joinSubPred(myschema, currsch) == null)
         return null;   // no term relates the two schemas, so this is not a join

      Map<Algorithm,Plan> candidates = new LinkedHashMap<>();
      if (JoinStrategy.allows(Algorithm.INDEX))
         addCandidate(candidates, Algorithm.INDEX, makeIndexJoin(current, currsch));
      if (JoinStrategy.allows(Algorithm.MERGE))
         addCandidate(candidates, Algorithm.MERGE, makeMergeJoin(current, currsch));
      if (JoinStrategy.allows(Algorithm.NESTEDLOOPS))
         addCandidate(candidates, Algorithm.NESTEDLOOPS, makeNestedLoopsJoin(current, currsch));
      if (JoinStrategy.current() == Algorithm.PRODUCT)
         // only on explicit request: a cross product plus a selection is not a join algorithm
         addCandidate(candidates, Algorithm.PRODUCT, makeProductJoin(current, currsch));

      if (candidates.isEmpty())
         // the forced algorithm does not apply here, so fall back to the
         // one algorithm that always does, and let the query run
         addCandidate(candidates, Algorithm.NESTEDLOOPS, makeNestedLoopsJoin(current, currsch));

      return chooseCheapest(candidates);
   }

   /**
    * Constructs a product plan of the specified plan and
    * this table.
    * @param current the specified plan
    * @return a product plan of the specified plan and this table
    */
   public Plan makeProductPlan(Plan current) {
      Plan p = addSelectPred(myplan);
      joinchoice = tblname + ": multibuffer product (no join predicate applies)";
      return new MultibufferProductPlan(tx, current, p);
   }

   /**
    * Describe how the most recent makeSelectPlan accessed this table.
    * Reporting is deferred to the query planner so that only the
    * alternative it actually keeps is announced.
    * @return the description, or null if there is nothing to report
    */
   String selectChoice() {
      return selectchoice;
   }

   /**
    * Describe how the most recent makeJoinPlan joined this table.
    * @return the description, or null if there is nothing to report
    */
   String joinChoice() {
      return joinchoice;
   }

   private Plan makeIndexSelect() {
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null) {
            IndexInfo ii = indexes.get(fldname);
            selectchoice = tblname + ": indexselect, " + describe(fldname, ii);
            return new IndexSelectPlan(myplan, ii, val);
         }
      }
      return null;
   }

   /**
    * Plan an indexjoin, using the cheapest index this table has on a
    * field equated with a field of the current plan.  Only equality
    * terms qualify, which Term guarantees by returning null from
    * equatesWithField for every other operator; a hash index could not
    * support anything else in any case.
    */
   private Plan makeIndexJoin(Plan current, Schema currsch) {
      IndexInfo bestii = null;
      String bestfld = null, bestouter = null;
      for (String fldname : indexes.keySet()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield == null || !currsch.hasField(outerfield))
            continue;
         IndexInfo ii = indexes.get(fldname);
         if (bestii == null || ii.blocksAccessed() < bestii.blocksAccessed()) {
            bestii = ii;
            bestfld = fldname;
            bestouter = outerfield;
         }
      }
      if (bestii == null)
         return null;
      indexjoindesc = describe(bestfld, bestii);
      // the RHS must stay a bare TablePlan: IndexJoinPlan opens it as a TableScan
      Plan p = new IndexJoinPlan(current, myplan, bestii, bestouter);
      p = addSelectPred(p);
      return addJoinPredExcluding(p, currsch, bestouter, bestfld);
   }

   /**
    * Plan a sort-merge join on an equality term relating this table to
    * the current plan.  MergeJoinPlan sorts both inputs itself.
    */
   private Plan makeMergeJoin(Plan current, Schema currsch) {
      String[] joinflds = findJoinFields(currsch);
      if (joinflds == null)
         return null;   // the tables are related, but not by an equality term
      Plan p = addSelectPred(myplan);
      Plan mj = new MergeJoinPlan(tx, current, p, joinflds[0], joinflds[1]);
      return addJoinPredExcluding(mj, currsch, joinflds[0], joinflds[1]);
   }

   /**
    * Plan a nested-loops join.  The join predicate is carried by the
    * plan and applied inside its scan, so -- unlike makeProductJoin --
    * there is no selection operation above the join.  This algorithm
    * always applies, including to inequality join terms.
    */
   private Plan makeNestedLoopsJoin(Plan current, Schema currsch) {
      Predicate joinpred = mypred.joinSubPred(currsch, myschema);
      Plan p = addSelectPred(myplan);
      return new NestedLoopsJoinPlan(current, p, joinpred);
   }

   private Plan makeProductJoin(Plan current, Schema currsch) {
      Plan p = makeProductPlan(current);
      return addJoinPred(p, currsch);
   }

   /**
    * Find a pair of fields, one from the current plan and one from this
    * table, that the predicate equates.
    * @return {the field of the current plan, the field of this table}, or null
    */
   private String[] findJoinFields(Schema currsch) {
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield))
            return new String[] { outerfield, fldname };
      }
      return null;
   }

   private void addCandidate(Map<Algorithm,Plan> candidates, Algorithm alg, Plan p) {
      if (p != null)
         candidates.put(alg, p);
   }

   private Plan chooseCheapest(Map<Algorithm,Plan> candidates) {
      Algorithm bestalg = null;
      Plan bestplan = null;
      StringBuilder considered = new StringBuilder();
      for (Map.Entry<Algorithm,Plan> e : candidates.entrySet()) {
         Plan p = e.getValue();
         if (considered.length() > 0)
            considered.append(", ");
         considered.append(shortLabel(e.getKey())).append("=").append(p.blocksAccessed()).append("b");
         if (bestplan == null || p.blocksAccessed() < bestplan.blocksAccessed()) {
            bestalg = e.getKey();
            bestplan = p;
         }
      }
      if (bestplan == null)
         return null;
      joinchoice = tblname + ": " + label(bestalg)
                 + (candidates.size() > 1 ? "  [considered " + considered + "]" : "");
      return bestplan;
   }

   /** The name of an algorithm, for the list of alternatives considered. */
   private String shortLabel(Algorithm a) {
      switch (a) {
         case INDEX:       return "indexjoin";
         case MERGE:       return "mergejoin";
         case NESTEDLOOPS: return "nestedloopsjoin";
         case PRODUCT:     return "productjoin";
         default:          return a.toString().toLowerCase();
      }
   }

   /** The name of an algorithm, plus any detail worth reporting about it. */
   private String label(Algorithm a) {
      if (a == Algorithm.INDEX && indexjoindesc != null)
         return shortLabel(a) + ", " + indexjoindesc;
      return shortLabel(a);
   }

   private String describe(String fldname, IndexInfo ii) {
      return "index on " + fldname + " used (" + ii.indexType() + ")";
   }

   private Plan addSelectPred(Plan p) {
      Predicate selectpred = mypred.selectSubPred(myschema);
      if (selectpred != null)
         return new SelectPlan(p, selectpred);
      else
         return p;
   }

   private Plan addJoinPred(Plan p, Schema currsch) {
      Predicate joinpred = mypred.joinSubPred(currsch, myschema);
      if (joinpred != null)
         return new SelectPlan(p, joinpred);
      else
         return p;
   }

   /**
    * Add the join predicate above the plan, minus the equality term the
    * join operator has already enforced.  Re-checking that term would be
    * wasted work, and would also make the plan's estimated output size
    * smaller than the plans that do not re-check it, distorting the
    * comparison in chooseCheapest.
    */
   private Plan addJoinPredExcluding(Plan p, Schema currsch, String currfld, String myfld) {
      Predicate joinpred = mypred.joinSubPredExcluding(currsch, myschema, currfld, myfld);
      if (joinpred != null)
         return new SelectPlan(p, joinpred);
      else
         return p;
   }
}
