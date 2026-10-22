package simpledb.plan;

import java.util.*;
import static java.sql.Types.INTEGER;

import simpledb.tx.Transaction;
import simpledb.materialize.*;
import simpledb.parse.AggregateSpec;
import simpledb.parse.QueryData;
import simpledb.query.SortField;
import simpledb.record.Schema;

public class ResultPlanner {

   public static Plan finish(Transaction tx, Plan p, QueryData data) {
      if (data.hasAggregation())
         p = aggregate(tx, p, data);
      if (data.isDistinct())
         return new DistinctPlan(tx, new ProjectPlan(p, data.fields()), distinctOrder(data));
      if (!data.sortFields().isEmpty() && !alreadyOrdered(data))
         p = new SortPlan(tx, p, data.sortFields());
      return new ProjectPlan(p, data.fields());
   }

   private static boolean alreadyOrdered(QueryData data) {
      List<SortField> sorts = data.sortFields();
      List<String> groups = data.groupFields();
      if (sorts.size() > groups.size())
         return false;
      for (int i = 0; i < sorts.size(); i++)
         if (!sorts.get(i).isAscending() || !sorts.get(i).field().equals(groups.get(i)))
            return false;
      return true;
   }

   private static List<SortField> distinctOrder(QueryData data) {
      List<SortField> order = new ArrayList<>(data.sortFields());
      for (String fldname : data.fields()) {
         boolean present = false;
         for (SortField sf : order)
            if (sf.field().equals(fldname))
               present = true;
         if (!present)
            order.add(new SortField(fldname));
      }
      return order;
   }

   private static Plan aggregate(Transaction tx, Plan p, QueryData data) {
      Schema sch = p.schema();
      for (String fldname : data.groupFields())
         requireField(sch, fldname);
      List<AggregationFn> fns = new ArrayList<>();
      for (AggregateSpec spec : data.aggregates())
         fns.add(function(spec, sch));
      return new GroupByPlan(tx, p, data.groupFields(), fns);
   }

   private static AggregationFn function(AggregateSpec spec, Schema sch) {
      if (!spec.isStar())
         requireField(sch, spec.field());
      switch (spec.function()) {
         case "count":
            return new CountFn(spec.argumentName());
         case "sum":
            requireInt(sch, spec);
            return new SumFn(spec.field());
         case "avg":
            requireInt(sch, spec);
            return new AvgFn(spec.field());
         case "min":
            return new MinFn(spec.field());
         case "max":
            return new MaxFn(spec.field());
         default:
            throw new RuntimeException("unknown aggregate function " + spec.function());
      }
   }

   private static void requireField(Schema sch, String fldname) {
      if (!sch.hasField(fldname))
         throw new RuntimeException("field " + fldname + " not found.");
   }

   private static void requireInt(Schema sch, AggregateSpec spec) {
      if (sch.type(spec.field()) != INTEGER)
         throw new RuntimeException(spec.function() + " requires an int field, but "
               + spec.field() + " is not one");
   }
}
