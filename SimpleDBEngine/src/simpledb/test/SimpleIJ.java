package simpledb.test;

import java.util.List;
import java.util.Scanner;
import static java.sql.Types.INTEGER;

import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;

/* A version of SimpleIJ that accesses the SimpleDB classes
 * directly, instead of connecting to the engine as a JDBC client.
 *
 * The JDBC version obtained column information from a
 * ResultSetMetaData object. Here that information comes from the
 * Plan's Schema, which is what EmbeddedMetaData wraps anyway.
 */

public class SimpleIJ {
   public static void main(String[] args) {
      Scanner sc = new Scanner(System.in);
      System.out.print("Database name (press Enter for studentdb)> ");
      String dbname = sc.nextLine().trim();
      if (dbname.isEmpty())
         dbname = "studentdb";

      // analogous to the driver and the connection
      SimpleDB db = new SimpleDB(dbname);

      System.out.print("\nSQL> ");
      while (sc.hasNextLine()) {
         // process one line of input
         String cmd = sc.nextLine().trim();
         if (cmd.startsWith("exit"))
            break;
         else if (cmd.startsWith("select"))
            doQuery(db, cmd);
         else if (!cmd.isEmpty())
            doUpdate(db, cmd);
         System.out.print("\nSQL> ");
      }
      sc.close();
   }

   private static void doQuery(SimpleDB db, String cmd) {
      Transaction tx = db.newTx();
      try {
         Plan p = db.planner().createQueryPlan(cmd, tx);

         // the schema plays the role of ResultSetMetaData
         Schema sch = p.schema();
         List<String> fields = sch.fields();

         // print header
         int totalwidth = 0;
         for (String fldname : fields) {
            int width = displaySize(sch, fldname);
            totalwidth += width;
            System.out.format("%" + width + "s", fldname);
         }
         System.out.println();
         for (int i = 0; i < totalwidth; i++)
            System.out.print("-");
         System.out.println();

         // print records
         Scan s = p.open();
         while (s.next()) {
            for (String fldname : fields) {
               String fmt = "%" + displaySize(sch, fldname);
               if (sch.type(fldname) == INTEGER)
                  System.out.format(fmt + "d", s.getInt(fldname));
               else
                  System.out.format(fmt + "s", s.getString(fldname));
            }
            System.out.println();
         }
         s.close();
         tx.commit();
      }
      catch (RuntimeException e) {
         System.out.println("SQL Exception: " + e.getMessage());
         tx.rollback();
      }
   }

   private static void doUpdate(SimpleDB db, String cmd) {
      Transaction tx = db.newTx();
      try {
         int howmany = db.planner().executeUpdate(cmd, tx);
         tx.commit();
         System.out.println(howmany + " records processed");
      }
      catch (RuntimeException e) {
         System.out.println("SQL Exception: " + e.getMessage());
         tx.rollback();
      }
   }

   /* The Schema has no equivalent of getColumnDisplaySize, so
    * this reproduces the calculation from EmbeddedMetaData:
    * integers get an arbitrary 6 characters, strings get their
    * declared varchar length, and the column is never narrower
    * than its own name.
    */
   private static int displaySize(Schema sch, String fldname) {
      int fldtype = sch.type(fldname);
      int fldlength = (fldtype == INTEGER) ? 6 : sch.length(fldname);
      return Math.max(fldname.length(), fldlength) + 1;
   }
}
