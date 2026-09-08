package simpledb.query;

import java.util.*;

/**
 * One entry of an <i>order by</i> clause: a field name
 * together with the direction it should be sorted in.
 * Ascending is the default, matching standard SQL.
 */
public class SortField {
   private String fldname;
   private boolean asc;

   /**
    * Create a sort field with an explicit direction.
    * @param fldname the name of the field to sort on
    * @param asc true for ascending order, false for descending
    */
   public SortField(String fldname, boolean asc) {
      this.fldname = fldname;
      this.asc = asc;
   }

   /**
    * Create a sort field in ascending order.
    * @param fldname the name of the field to sort on
    */
   public SortField(String fldname) {
      this(fldname, true);
   }

   /**
    * Return the name of the field being sorted on.
    * @return the field name
    */
   public String field() {
      return fldname;
    }

   /**
    * Return whether this field is sorted in ascending order.
    * @return true if ascending, false if descending
    */
   public boolean isAscending() {
      return asc;
   }

   /**
    * Convert a plain list of field names into a list of
    * ascending sort fields.  Used by operators such as
    * GroupByPlan and MergeJoinPlan, which sort only to bring
    * equal values together and so never need descending order.
    * @param fldnames the field names
    * @return the corresponding ascending sort fields
    */
   public static List<SortField> ascending(List<String> fldnames) {
      List<SortField> result = new ArrayList<>();
      for (String fldname : fldnames)
         result.add(new SortField(fldname, true));
      return result;
   }

   public String toString() {
      return fldname + (asc ? "" : " desc");
   }
}
