package simpledb.materialize;

import java.util.*;
import simpledb.query.*;
import simpledb.record.Schema;

public class HashJoinScan implements Scan {
   private List<TempTable> probeparts, buildparts;
   private Schema probesch, buildsch;
   private String probefld, buildfld;
   private Map<String,Integer> buildpos = new HashMap<>();
   private Map<Constant,List<Constant[]>> table = new HashMap<>();
   private int partition;
   private Scan probe;
   private List<Constant[]> matches;
   private int matchidx;
   private Constant[] currentbuild;

   public HashJoinScan(List<TempTable> probeparts, Schema probesch, String probefld,
                       List<TempTable> buildparts, Schema buildsch, String buildfld) {
      this.probeparts = probeparts;
      this.probesch = probesch;
      this.probefld = probefld;
      this.buildparts = buildparts;
      this.buildsch = buildsch;
      this.buildfld = buildfld;
      int i = 0;
      for (String fldname : buildsch.fields())
         buildpos.put(fldname, i++);
      beforeFirst();
   }

   public void beforeFirst() {
      closeProbe();
      table.clear();
      partition = -1;
      matches = null;
      matchidx = 0;
      currentbuild = null;
   }

   public boolean next() {
      while (true) {
         if (matches != null && matchidx < matches.size()) {
            currentbuild = matches.get(matchidx++);
            return true;
         }
         if (probe != null && probe.next()) {
            matches = table.get(probe.getVal(probefld));
            matchidx = 0;
            continue;
         }
         if (!openNextPartition())
            return false;
      }
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   public Constant getVal(String fldname) {
      if (probesch.hasField(fldname))
         return probe.getVal(fldname);
      Integer pos = buildpos.get(fldname);
      if (pos == null)
         throw new RuntimeException("field " + fldname + " not found.");
      return currentbuild[pos];
   }

   public boolean hasField(String fldname) {
      return probesch.hasField(fldname) || buildsch.hasField(fldname);
   }

   public void close() {
      closeProbe();
      table.clear();
   }

   private boolean openNextPartition() {
      closeProbe();
      table.clear();
      matches = null;
      matchidx = 0;
      partition++;
      if (partition >= probeparts.size())
         return false;
      buildTable(buildparts.get(partition));
      probe = probeparts.get(partition).open();
      probe.beforeFirst();
      return true;
   }

   private void buildTable(TempTable part) {
      Scan s = part.open();
      List<String> fields = buildsch.fields();
      while (s.next()) {
         Constant[] row = new Constant[fields.size()];
         for (int i = 0; i < row.length; i++)
            row[i] = s.getVal(fields.get(i));
         table.computeIfAbsent(row[buildpos.get(buildfld)], k -> new ArrayList<>()).add(row);
      }
      s.close();
   }

   private void closeProbe() {
      if (probe != null) {
         probe.close();
         probe = null;
      }
   }
}
