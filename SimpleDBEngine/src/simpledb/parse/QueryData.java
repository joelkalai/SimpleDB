package simpledb.parse;

import java.util.*;

import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private List<String> fields;
   private Collection<String> tables;
   private Predicate pred;
   private List<SortField> sortfields;
   private List<String> groupfields;
   private List<AggregateSpec> aggregates;
   private boolean distinct;
   
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                    List<SortField> sortfields, List<String> groupfields,
                    List<AggregateSpec> aggregates, boolean distinct) {
      this.fields = fields;
      this.distinct = distinct;
      this.tables = tables;
      this.pred = pred;
      this.sortfields = sortfields;
      this.groupfields = groupfields;
      this.aggregates = aggregates;
   }
   
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                    List<SortField> sortfields, List<String> groupfields,
                    List<AggregateSpec> aggregates) {
      this(fields, tables, pred, sortfields, groupfields, aggregates, false);
   }
   
   /**
    * Saves the field and table list, the predicate,
    * and the order-by fields.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                    List<SortField> sortfields) {
      this(fields, tables, pred, sortfields, new ArrayList<String>(), new ArrayList<AggregateSpec>());
   }
   
   /**
    * Saves the field and table list and predicate,
    * for a query with no order by clause.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
      this(fields, tables, pred, new ArrayList<SortField>());
   }
   
   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
   }
   
   /**
    * Returns the tables mentioned in the from clause.
    * @return a collection of table names
    */
   public Collection<String> tables() {
      return tables;
   }
   
   /**
    * Returns the predicate that describes which
    * records should be in the output table.
    * @return the query predicate
    */
   public Predicate pred() {
      return pred;
   }
   
   /**
    * Returns the fields mentioned in the order by clause,
    * each paired with its sort direction.  The list is empty
    * when the query has no order by clause.
    * @return a list of sort fields
    */
   public List<SortField> sortFields() {
      return sortfields;
   }
   
   public List<String> groupFields() {
      return groupfields;
   }
   
   public List<AggregateSpec> aggregates() {
      return aggregates;
   }
   
   public boolean isDistinct() {
      return distinct;
   }
   
   public boolean hasAggregation() {
      return !aggregates.isEmpty() || !groupfields.isEmpty();
   }
   
   private String display(String fldname) {
      for (AggregateSpec spec : aggregates)
         if (spec.fieldName().equals(fldname))
            return spec.toString();
      return fldname;
   }
   
   public String toString() {
      String result = distinct ? "select distinct " : "select ";
      for (String fldname : fields)
         result += display(fldname) + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
      if (!groupfields.isEmpty()) {
         result += " group by ";
         for (String fldname : groupfields)
            result += fldname + ", ";
         result = result.substring(0, result.length()-2); //remove final comma
      }
      if (!sortfields.isEmpty()) {
         result += " order by ";
         for (SortField sf : sortfields)
            result += display(sf.field()) + (sf.isAscending() ? "" : " desc") + ", ";
         result = result.substring(0, result.length()-2); //remove final comma
      }
      return result;
   }
}
