package simpledb.parse;

import java.util.Arrays;
import java.util.List;

public class AggregateSpec {
   public static final List<String> FUNCTIONS = Arrays.asList("count", "sum", "avg", "min", "max");

   private String function;
   private String field;

   public AggregateSpec(String function, String field) {
      this.function = function;
      this.field = field;
   }

   public String function() {
      return function;
   }

   public String field() {
      return field;
   }

   public boolean isStar() {
      return field.equals("*");
   }

   public String argumentName() {
      return isStar() ? "all" : field;
   }

   public String fieldName() {
      return function + "of" + argumentName();
   }

   public boolean equals(Object obj) {
      if (!(obj instanceof AggregateSpec))
         return false;
      return fieldName().equals(((AggregateSpec) obj).fieldName());
   }

   public int hashCode() {
      return fieldName().hashCode();
   }

   public String toString() {
      return function + "(" + field + ")";
   }
}
