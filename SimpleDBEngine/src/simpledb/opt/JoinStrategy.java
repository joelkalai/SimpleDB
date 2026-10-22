package simpledb.opt;

/**
 * Selects which join algorithm the optimizer is allowed to consider.
 * <P>
 * The default is {@link Algorithm#AUTO}, in which case
 * {@link TablePlanner#makeJoinPlan} builds every applicable join plan
 * and keeps the cheapest one.  Forcing a single algorithm is useful for
 * testing: the student database is small enough that one algorithm wins
 * on cost for nearly every query, so forcing is the only practical way
 * to exercise and compare the others.
 * <P>
 * The algorithm can be forced from the command line with
 * <pre>  java -Dsimpledb.join=mergejoin ...</pre>
 * or programmatically with {@link #force}.
 */
public class JoinStrategy {
   /** The join algorithms a TablePlanner knows how to build. */
   public enum Algorithm { AUTO, INDEX, MERGE, HASH, NESTEDLOOPS, PRODUCT }

   /** The system property consulted for the initial setting. */
   public static final String PROPERTY = "simpledb.join";

   private static Algorithm current = fromProperty();

   /**
    * Return the algorithm currently in force.
    * @return the current setting, AUTO if the optimizer is free to choose
    */
   public static Algorithm current() {
      return current;
   }

   /**
    * Restrict the optimizer to the specified algorithm.
    * @param a the algorithm to force, or AUTO to let the optimizer choose
    */
   public static void force(Algorithm a) {
      current = (a == null) ? Algorithm.AUTO : a;
   }

   /**
    * Return true if the specified algorithm may be considered.
    * @param a the algorithm
    * @return true if the setting is AUTO or names this algorithm
    */
   public static boolean allows(Algorithm a) {
      return current == Algorithm.AUTO || current == a;
   }

   /**
    * Parse an algorithm name, tolerating the usual spellings.
    * @param s the name
    * @return the algorithm, or null if the name is not recognized
    */
   public static Algorithm parse(String s) {
      if (s == null)
         return null;
      String key = s.trim().toLowerCase().replace("-", "").replace("_", "").replace(" ", "");
      switch (key) {
         case "auto":                                      return Algorithm.AUTO;
         case "index": case "indexjoin":                    return Algorithm.INDEX;
         case "merge": case "mergejoin": case "sortmerge":  return Algorithm.MERGE;
         case "hash": case "hashjoin": case "partition":
         case "partitionjoin": case "gracehash":            return Algorithm.HASH;
         case "nl": case "nested": case "nestedloop":
         case "nestedloops": case "nestedloopsjoin":        return Algorithm.NESTEDLOOPS;
         case "product": case "crossproduct":               return Algorithm.PRODUCT;
         default:                                           return null;
      }
   }

   private static Algorithm fromProperty() {
      String s = System.getProperty(PROPERTY);
      if (s == null)
         return Algorithm.AUTO;
      Algorithm a = parse(s);
      if (a == null) {
         System.out.println("unknown " + PROPERTY + " value \"" + s + "\"; using auto");
         return Algorithm.AUTO;
      }
      return a;
   }
}
