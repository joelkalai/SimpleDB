package embedded;
import java.sql.*;
import simpledb.jdbc.embedded.EmbeddedDriver;

public class PlusDemo {
   private static final String[] QUERIES = {
      "select dname, count(sid), min(gradyear), max(gradyear) from student, dept where majorid = did group by dname order by count(sid) desc",
      "select count(*), avg(gradyear) from student",
      "select distinct gradyear from student order by gradyear desc",
      "explain select dname, count(sid) from student, dept where majorid = did group by dname"
   };

   public static void main(String[] args) {
      String url = "jdbc:simpledb:studentdb";
      Driver d = new EmbeddedDriver();
      try (Connection conn = d.connect(url, null);
            Statement stmt = conn.createStatement()) {
         for (String qry : QUERIES) {
            System.out.println();
            System.out.println(qry);
            try (ResultSet rs = stmt.executeQuery(qry)) {
               ResultSetMetaData md = rs.getMetaData();
               int numcols = md.getColumnCount();
               while (rs.next()) {
                  StringBuilder sb = new StringBuilder();
                  for (int i = 1; i <= numcols; i++) {
                     String fldname = md.getColumnName(i);
                     if (i > 1)
                        sb.append("\t");
                     if (md.getColumnType(i) == Types.INTEGER)
                        sb.append(rs.getInt(fldname));
                     else
                        sb.append(rs.getString(fldname));
                  }
                  System.out.println(sb);
               }
            }
         }
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }
}
