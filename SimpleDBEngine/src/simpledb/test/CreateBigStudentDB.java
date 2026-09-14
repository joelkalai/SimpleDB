package simpledb.test;

import java.util.Random;
import simpledb.plan.Planner;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Creates a larger version of the student database, in a separate
 * directory, for exercising the join algorithms on data that does not
 * fit in a couple of blocks.
 * <P>
 * The values are deliberately unordered, so that sorting an input
 * produces many initial runs and the mergejoin's SortScan has to merge
 * them -- the case the small student database never reaches.  The data
 * is generated from a fixed seed, so runs are reproducible.
 * <P>
 * The load is committed in batches: SimpleDB is configured with only
 * eight buffers, and a single transaction spanning thousands of index
 * and record page pins exhausts them.
 */
public class CreateBigStudentDB {
   public static final String DIRNAME = "bigstudentdb";
   private static final int NUM_DEPTS    = 30;
   private static final int NUM_STUDENTS = 600;
   private static final int NUM_COURSES  = 90;
   private static final int NUM_ENROLLS  = 900;
   private static final int BATCH        = 50;
   private static final int BUFFERS      = 64;

   private static SimpleDB db;
   private static Planner planner;
   private static Transaction tx;
   private static int pending = 0;

   public static void main(String[] args) {
      String dirname = (args.length > 0) ? args[0] : DIRNAME;
      try {
         // A btree insert pins a leaf and a directory page on top of the
         // record and catalog pages, and the eight-buffer default is not
         // enough headroom for a bulk load.
         SimpleDB.BUFFER_SIZE = Math.max(SimpleDB.BUFFER_SIZE, BUFFERS);
         db = new SimpleDB(dirname);
         planner = db.planner();
         tx = db.newTx();
         Random rand = new Random(3223);

         exec("create table DEPT(DId int, DName varchar(8))");
         for (int i = 0; i < NUM_DEPTS; i++)
            exec("insert into DEPT(DId, DName) values ("
                  + (10 + i) + ", 'dept" + i + "')");
         System.out.println(NUM_DEPTS + " DEPT records inserted.");

         exec("create table STUDENT(SId int, SName varchar(10), MajorId int, GradYear int)");
         exec("create index idx_majorid on STUDENT(MajorId) using hash");
         for (int i = 0; i < NUM_STUDENTS; i++)
            exec("insert into STUDENT(SId, SName, MajorId, GradYear) values ("
                  + (i + 1) + ", 'stud" + i + "', "
                  + (10 + rand.nextInt(NUM_DEPTS)) + ", "
                  + (2018 + rand.nextInt(5)) + ")");
         System.out.println(NUM_STUDENTS + " STUDENT records inserted.");

         exec("create table COURSE(CId int, Title varchar(20), DeptId int)");
         for (int i = 0; i < NUM_COURSES; i++)
            exec("insert into COURSE(CId, Title, DeptId) values ("
                  + (i + 1) + ", 'course" + i + "', "
                  + (10 + rand.nextInt(NUM_DEPTS)) + ")");
         System.out.println(NUM_COURSES + " COURSE records inserted.");

         exec("create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))");
         exec("create index idx_studentid on ENROLL(StudentId) using btree");
         String[] grades = {"A", "B+", "B", "C+", "C", "D"};
         for (int i = 0; i < NUM_ENROLLS; i++)
            exec("insert into ENROLL(EId, StudentId, SectionId, Grade) values ("
                  + (i + 1) + ", " + (1 + rand.nextInt(NUM_STUDENTS)) + ", "
                  + (13 + rand.nextInt(40)) + ", '" + grades[rand.nextInt(grades.length)] + "')");
         System.out.println(NUM_ENROLLS + " ENROLL records inserted.");

         tx.commit();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   /** Run one update, committing and starting a fresh transaction every BATCH statements. */
   private static void exec(String cmd) {
      planner.executeUpdate(cmd, tx);
      if (++pending >= BATCH) {
         tx.commit();
         tx = db.newTx();
         pending = 0;
      }
   }
}
