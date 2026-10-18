package simpledb.plan;

import java.util.Arrays;
import java.util.List;
import simpledb.query.Scan;
import simpledb.record.Schema;

public class ExplainPlan implements Plan {
   public static final String FIELD = "queryplan";

   private Plan p;
   private List<String> lines;
   private Schema sch = new Schema();

   public ExplainPlan(Plan p) {
      this.p = p;
      this.lines = PlanPrinter.lines(p);
      int width = 1;
      for (String line : lines)
         width = Math.max(width, line.length());
      sch.addStringField(FIELD, width);
   }

   public Scan open() {
      return new ExplainScan(lines);
   }

   public int blocksAccessed() {
      return 0;
   }

   public int recordsOutput() {
      return lines.size();
   }

   public int distinctValues(String fldname) {
      return lines.size();
   }

   public Schema schema() {
      return sch;
   }

   public String describe() {
      return "ExplainPlan";
   }

   public List<Plan> children() {
      return Arrays.asList(p);
   }
}
