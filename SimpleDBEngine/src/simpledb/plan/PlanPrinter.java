package simpledb.plan;

import java.util.ArrayList;
import java.util.List;

public class PlanPrinter {

   public static List<String> lines(Plan p) {
      List<String> out = new ArrayList<>();
      print(p, "", "", out);
      return out;
   }

   public static String format(Plan p) {
      return String.join(System.lineSeparator(), lines(p));
   }

   private static void print(Plan p, String firstPrefix, String restPrefix, List<String> out) {
      out.add(firstPrefix + p.describe() + "  (blocks=" + p.blocksAccessed()
            + ", rows=" + p.recordsOutput() + ")");
      List<Plan> kids = p.children();
      for (int i = 0; i < kids.size(); i++) {
         boolean last = (i == kids.size() - 1);
         print(kids.get(i), restPrefix + "+- ", restPrefix + (last ? "   " : "|  "), out);
      }
   }
}
