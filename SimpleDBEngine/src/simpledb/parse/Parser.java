package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;

/**
 * The SimpleDB parser.
 * @author Edward Sciore
 */
public class Parser {
   private Lexer lex;
   
   public Parser(String s) {
      lex = new Lexer(s);
   }
   
// Methods for parsing predicates, terms, expressions, constants, and fields
   
   public String field() {
      return lex.eatId();
   }
   
   public Constant constant() {
      if (lex.matchStringConstant())
         return new Constant(lex.eatStringConstant());
      else
         return new Constant(lex.eatIntConstant());
   }
   
   public Expression expression() {
      if (lex.matchId())
         return new Expression(field());
      else
         return new Expression(constant());
   }
   
   public Term term() {
      Expression lhs = expression();
      String opr = lex.eatOpr();
      Expression rhs = expression();
      return new Term(lhs, opr, rhs);
   }
   
   public Predicate predicate() {
      Predicate pred = new Predicate(term());
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         pred.conjoinWith(predicate());
      }
      return pred;
   }
   
// Methods for parsing queries
   
   public QueryData query() {
      lex.eatKeyword("select");
      List<String> fields = new ArrayList<String>();
      Map<String,AggregateSpec> aggregates = new LinkedHashMap<String,AggregateSpec>();
      selectList(fields, aggregates);
      lex.eatKeyword("from");
      Collection<String> tables = tableList();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      List<String> groupfields = new ArrayList<String>();
      if (lex.matchKeyword("group")) {
         lex.eatKeyword("group");
         lex.eatKeyword("by");
         groupfields = fieldList();
      }
      List<SortField> sortfields = new ArrayList<SortField>();
      if (lex.matchKeyword("order")) {
         lex.eatKeyword("order");
         lex.eatKeyword("by");
         sortfields = sortList(aggregates);
      }
      checkGrouping(fields, aggregates, groupfields, sortfields);
      return new QueryData(fields, tables, pred, sortfields, groupfields,
                           new ArrayList<AggregateSpec>(aggregates.values()));
   }

   private boolean matchAggregate() {
      for (String fn : AggregateSpec.FUNCTIONS)
         if (lex.matchKeyword(fn))
            return true;
      return false;
   }

   private AggregateSpec aggregate() {
      String fn = null;
      for (String f : AggregateSpec.FUNCTIONS)
         if (lex.matchKeyword(f))
            fn = f;
      lex.eatKeyword(fn);
      lex.eatDelim('(');
      String arg;
      if (lex.matchDelim('*')) {
         if (!fn.equals("count"))
            throw new BadSyntaxException("only count accepts *");
         lex.eatDelim('*');
         arg = "*";
      }
      else
         arg = field();
      lex.eatDelim(')');
      return new AggregateSpec(fn, arg);
   }

   private void checkGrouping(List<String> fields, Map<String,AggregateSpec> aggregates,
                              List<String> groupfields, List<SortField> sortfields) {
      if (aggregates.isEmpty() && groupfields.isEmpty())
         return;
      for (String fldname : fields)
         requireGrouped(fldname, aggregates, groupfields);
      for (SortField sf : sortfields)
         requireGrouped(sf.field(), aggregates, groupfields);
   }

   private void requireGrouped(String fldname, Map<String,AggregateSpec> aggregates,
                               List<String> groupfields) {
      if (!aggregates.containsKey(fldname) && !groupfields.contains(fldname))
         throw new BadSyntaxException("field " + fldname
               + " must appear in the group by clause or inside an aggregate");
   }
   
   /**
    * Parse a comma-separated list of order by entries.
    * Each entry is a field name optionally followed by
    * asc or desc; ascending is the default.
    */
   private List<SortField> sortList(Map<String,AggregateSpec> aggregates) {
      List<SortField> L = new ArrayList<SortField>();
      String fldname;
      if (matchAggregate()) {
         AggregateSpec spec = aggregate();
         aggregates.put(spec.fieldName(), spec);
         fldname = spec.fieldName();
      }
      else
         fldname = field();
      boolean asc = true;
      if (lex.matchKeyword("asc"))
         lex.eatKeyword("asc");
      else if (lex.matchKeyword("desc")) {
         lex.eatKeyword("desc");
         asc = false;
      }
      L.add(new SortField(fldname, asc));
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(sortList(aggregates));
      }
      return L;
   }
   
   private void selectList(List<String> fields, Map<String,AggregateSpec> aggregates) {
      if (matchAggregate()) {
         AggregateSpec spec = aggregate();
         aggregates.put(spec.fieldName(), spec);
         fields.add(spec.fieldName());
      }
      else
         fields.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         selectList(fields, aggregates);
      }
   }
   
   private Collection<String> tableList() {
      Collection<String> L = new ArrayList<String>();
      L.add(lex.eatId());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(tableList());
      }
      return L;
   }
   
// Methods for parsing the various update commands
   
   public Object updateCmd() {
      if (lex.matchKeyword("insert"))
         return insert();
      else if (lex.matchKeyword("delete"))
         return delete();
      else if (lex.matchKeyword("update"))
         return modify();
      else
         return create();
   }
   
   private Object create() {
      lex.eatKeyword("create");
      if (lex.matchKeyword("table"))
         return createTable();
      else if (lex.matchKeyword("view"))
         return createView();
      else
         return createIndex();
   }
   
// Method for parsing delete commands
   
   public DeleteData delete() {
      lex.eatKeyword("delete");
      lex.eatKeyword("from");
      String tblname = lex.eatId();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new DeleteData(tblname, pred);
   }
   
// Methods for parsing insert commands
   
   public InsertData insert() {
      lex.eatKeyword("insert");
      lex.eatKeyword("into");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      List<String> flds = fieldList();
      lex.eatDelim(')');
      lex.eatKeyword("values");
      lex.eatDelim('(');
      List<Constant> vals = constList();
      lex.eatDelim(')');
      return new InsertData(tblname, flds, vals);
   }
   
   private List<String> fieldList() {
      List<String> L = new ArrayList<String>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(fieldList());
      }
      return L;
   }
   
   private List<Constant> constList() {
      List<Constant> L = new ArrayList<Constant>();
      L.add(constant());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(constList());
      }
      return L;
   }
   
// Method for parsing modify commands
   
   public ModifyData modify() {
      lex.eatKeyword("update");
      String tblname = lex.eatId();
      lex.eatKeyword("set");
      String fldname = field();
      lex.eatDelim('=');
      Expression newval = expression();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new ModifyData(tblname, fldname, newval, pred);
   }
   
// Method for parsing create table commands
   
   public CreateTableData createTable() {
      lex.eatKeyword("table");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      Schema sch = fieldDefs();
      lex.eatDelim(')');
      return new CreateTableData(tblname, sch);
   }
   
   private Schema fieldDefs() {
      Schema schema = fieldDef();
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         Schema schema2 = fieldDefs();
         schema.addAll(schema2);
      }
      return schema;
   }
   
   private Schema fieldDef() {
      String fldname = field();
      return fieldType(fldname);
   }
   
   private Schema fieldType(String fldname) {
      Schema schema = new Schema();
      if (lex.matchKeyword("int")) {
         lex.eatKeyword("int");
         schema.addIntField(fldname);
      }
      else {
         lex.eatKeyword("varchar");
         lex.eatDelim('(');
         int strLen = lex.eatIntConstant();
         lex.eatDelim(')');
         schema.addStringField(fldname, strLen);
      }
      return schema;
   }
   
// Method for parsing create view commands
   
   public CreateViewData createView() {
      lex.eatKeyword("view");
      String viewname = lex.eatId();
      lex.eatKeyword("as");
      QueryData qd = query();
      return new CreateViewData(viewname, qd);
   }
   
   
//  Method for parsing create index commands
   
   public CreateIndexData createIndex() {
      lex.eatKeyword("index");
      String idxname = lex.eatId();
      lex.eatKeyword("on");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      String fldname = field();
      lex.eatDelim(')');
      String idxtype = "hash";  // default when "using" clause is omitted
      if (lex.matchKeyword("using")) {
         lex.eatKeyword("using");
         if (lex.matchKeyword("btree")) {
            lex.eatKeyword("btree");
            idxtype = "btree";
         } else {
            lex.eatKeyword("hash");
            idxtype = "hash";
         }
      }
      return new CreateIndexData(idxname, tblname, fldname, idxtype);
   }
}

