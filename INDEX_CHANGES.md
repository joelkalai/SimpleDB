# Index Structures — Changes & Testing

Activates SimpleDB's index-aware query/update planners (previously the
index code existed but was never used) and extends it to support **hash
and btree indexes simultaneously** on different attributes, per the
assignment at
https://www.comp.nus.edu.sg/~tankl/cs3223/project/index.htm

Builds on the lab1 non-equality-predicate work (see `NON_EQUALITY_CHANGES.md`).
The requirement "non-equality predicates must not use an index" needed no
new code here: `query/Term.equatesWithConstant()`/`equatesWithField()`
already return `null` unless the term's operator is `"="`, and
`opt/TablePlanner.java` only offers the index path through those two
methods — so a `<`/`>`/`!=` predicate on an indexed field already falls
through to a plain table scan. `IndexTypeTest.java` (below) asserts this
directly.

## Files changed

### `SimpleDBEngine/src/simpledb/server/SimpleDB.java`
Swapped the active planner pair: `HeuristicQueryPlanner` +
`IndexUpdatePlanner` are now active; `BasicQueryPlanner`/`BasicUpdatePlanner`
(index-blind) are commented out. This is the switch that makes every
other change in this document actually take effect — without it, indexes
are created and maintained but the query planner never looks at them.

### `SimpleDBEngine/src/simpledb/metadata/IndexInfo.java`
Previously hardcoded `open()` to always `return new HashIndex(...)` (with
`BTreeIndex` imported but only reachable via a commented-out line), so
only one index type could ever exist. Added:
- `HASH`/`BTREE` string constants.
- An `idxtype` field, set from a new constructor parameter (unrecognized
  or `null` values default to `HASH`, preserving the old behavior).
- `open()` and `blocksAccessed()` now branch on `idxtype` to construct/cost
  either a `HashIndex` or a `BTreeIndex`.
- A new `indexType()` accessor.

### `SimpleDBEngine/src/simpledb/metadata/IndexMgr.java`
The `idxcat` catalog table only stored `indexname`/`tablename`/`fieldname` —
no way to remember which type an index was. Added a 4th string column,
`indextype`. `createIndex()` gained an `idxtype` parameter (defaults to
`"hash"` if `null`) and writes it to the new column; `getIndexInfo()`
reads it back and passes it into `IndexInfo`'s constructor.

### `SimpleDBEngine/src/simpledb/metadata/MetadataMgr.java`
One-line signature change: `createIndex()` gained an `idxtype` parameter,
passed straight through to `IndexMgr`.

### `SimpleDBEngine/src/simpledb/parse/Lexer.java`
Added `using`, `hash`, `btree` to `initKeywords()`, needed to parse the
new optional clause below.

### `SimpleDBEngine/src/simpledb/parse/CreateIndexData.java`
Added an `idxtype` field + `indexType()` accessor; the constructor now
takes 4 args instead of 3. Safe to change unconditionally — grepping the
whole repo confirms `Parser.createIndex()` is the *only* place that
constructs a `CreateIndexData`.

### `SimpleDBEngine/src/simpledb/parse/Parser.java`
`createIndex()` now accepts an optional `using hash` / `using btree`
clause after the field list:
```java
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
```
Grammar: `create index <idx> on <tbl>(<fld>) [using (hash|btree)]`.
Omitting `using` defaults to `hash`, so every pre-existing `create index`
statement (there were none in this repo, but any external ones) keeps
working unchanged.

### `SimpleDBEngine/src/simpledb/plan/BasicUpdatePlanner.java` and `SimpleDBEngine/src/simpledb/index/planner/IndexUpdatePlanner.java`
Both `executeCreateIndex()` methods now pass `data.indexType()` through to
`mdm.createIndex(...)`.

### `SimpleDBEngine/src/simpledb/metadata/MetadataMgrTest.java`
The pre-existing index-metadata test called `mdm.createIndex(...)` twice
with no type. Updated to pass `"hash"` for one index and `"btree"` for
the other — this incidentally becomes a second, independent proof that
both types work, exercised at the metadata-API layer instead of via SQL.

### `SimpleDBEngine/src/simpledb/test/CreateStudentDB.java` (and the JDBC copies below)
Added two `create index` statements, each placed **before** its table's
insert loop (index population only happens as rows are inserted once
`IndexUpdatePlanner` is active — there is no backfill for rows already in
the table):
- `create index idx_majorid on STUDENT(MajorId) using hash` — satisfies
  the "at least MajorId" requirement, using the same type that was
  hardcoded before this change.
- `create index idx_studentid on ENROLL(StudentId) using btree` —
  required by `IndexSelectTest`/`IndexJoinTest`, and demonstrates a hash
  index and a btree index coexisting in the same database, which is the
  actual point of requirement 4.

### `SimpleDBClients/src/embedded/CreateStudentDB.java` and `SimpleDBClients/src/network/CreateStudentDB.java`
Same two `create index` statements as above, added for consistency with
the engine copy (not part of the graded test list, but these files would
otherwise silently drift out of sync).

### `SimpleDBEngine/src/simpledb/index/IndexTypeTest.java` (new)
Real end-to-end execution test (not just grammar acceptance), following
the `NonEqualityScanTest.java` PASS/FAIL convention from lab1. Creates a
scratch table with a hash index on one column and a btree index on
another, then asserts:
- `IndexInfo.indexType()` correctly reports `"hash"`/`"btree"` for each.
- An equality predicate on either indexed field triggers
  `TablePlanner`'s `"index on <field> used"` message (i.e. the index path
  is actually taken) and returns the correct row.
- A non-equality predicate (`<`) on the **same** indexed field does *not*
  trigger that message (i.e. lab1's equality-only gating still holds
  through this change) while still returning the correct rows.

### `.gitignore`
Added `indextypetest/`, the throwaway database directory created by the
new test.

## Out of scope (not changed)

`opt/TablePlanner.java`, `opt/HeuristicQueryPlanner.java`,
`index/planner/IndexUpdatePlanner.java` (aside from the one
`executeCreateIndex` line), `index/hash/HashIndex.java`,
`index/btree/*.java`, `index/query/{IndexJoinScan,IndexSelectScan}.java` —
all already correct and fully implemented; this assignment was about
wiring the index-aware planners in and letting an index be either type,
not about the index data structures themselves.

## A required one-time step: delete `studentdb/`

`idxcat`'s schema changed (new `indextype` column). SimpleDB does not
migrate schemas: a table's field list is read from the `fldcat` system
table on disk, and `IndexMgr` only (re)defines `idxcat`'s columns when the
whole database directory is brand new. An existing `studentdb/` directory
from before this change has a 3-column `idxcat` on disk that is
incompatible with the new code and must be deleted, not patched:

```bash
rm -rf SimpleDBEngine/studentdb SimpleDBClients/studentdb
```

Both are already covered by `.gitignore`, so this is filesystem cleanup
only. (No other test directory's schema changed, so nothing else needs
deleting — `metadatamgrtest/` was deleted once during this session's
development only because `MetadataMgrTest.java`'s content changed, not
because of a schema change; it regenerates fine on the next run either
way.)

## How to test

All commands below assume `cd SimpleDBEngine`.

| Test | What it actually exercises | What it does **NOT** verify |
|------|------------------------------|-------------------------------|
| `ParserTest` | Grammar acceptance: does `create index ... [using hash\|btree]` parse? Prints `yes`/`no`. | Whether the index is actually created with the right type, or used by any query |
| `CreateStudentDB` | Real execution: creates the two indexes and populates them as rows are inserted | Whether the planner ever uses them |
| `IndexRetrievalTest` / `IndexUpdateTest` | Real execution against the hash index on `STUDENT.MajorId`, via direct `Index` API calls (bypassing the parser/planner) | Whether the SQL grammar or the heuristic query planner work — these call `Index` methods directly |
| `IndexSelectTest` / `IndexJoinTest` | Real execution against the btree index on `ENROLL.StudentId`, via both a manual `Index` walk and `IndexSelectPlan`/`IndexJoinPlan` | Only exercises one index (btree); says nothing about hash |
| `IndexTypeTest` (new) | Real execution through actual SQL and the actual heuristic planner: both index types resolving equality correctly, **and** confirming a non-equality predicate on an indexed field does not use the index | This is the only test that exercises the planner's index-selection decision (the `"index on X used"` message) directly |
| `MetadataMgrTest` | Real execution of the metadata-layer API (`MetadataMgr.createIndex`/`getIndexInfo`) with explicit hash/btree types, independent of SQL | Nothing about SQL parsing or the query planner |

### 1. Delete stale state (required once, see above), then compile
```bash
rm -rf studentdb metadatamgrtest indextypetest
cd /Users/ben/eclipse-workspace/cs3223/SimpleDBClients && rm -rf studentdb && cd ../SimpleDBEngine
javac -d bin $(find src -name "*.java")
```

### 2. Grammar acceptance — `ParserTest`
```bash
printf \
'create index idx_a on t(a)
create index idx_a on t(a) using hash
create index idx_b on t(b) using btree
' | java -cp bin simpledb.parse.ParserTest
```
Expect `yes` on all three lines.

### 3. Rebuild `studentdb` with the new index-creation SQL
```bash
java -cp bin simpledb.test.CreateStudentDB
```
Expect `Index on STUDENT.MajorId created (hash).` and `Index on
ENROLL.StudentId created (btree).` interleaved with the existing output.

### 4. Provided index tests
```bash
java -cp bin simpledb.index.IndexRetrievalTest   # expect: amy, sue, kim, pat
java -cp bin simpledb.index.IndexUpdateTest      # expect: sam present, joe absent
java -cp bin simpledb.index.query.IndexSelectTest  # expect: A, A
java -cp bin simpledb.index.query.IndexJoinTest    # expect: matching grades printed twice
```

### 5. New coverage — type coexistence and non-equality bypass
```bash
java -cp bin simpledb.index.IndexTypeTest
```
Expect `ALL PASS`. A `FAIL` on `"range predicate on a does NOT use the
index"` would mean lab1's equality-only gating regressed; a `FAIL` on
either `"equality on ... uses the ... index"` line would mean the
planner swap in `SimpleDB.java` isn't actually active.

### 6. Metadata-layer regression, both types via direct API
```bash
java -cp bin simpledb.metadata.MetadataMgrTest
```
`B(indexA)`/`R(indexA)`/... and the `indexB` equivalents are now backed
by a real hash index and a real btree index respectively (previously
both were silently hash).

### 7. `SimpleDBClients` sanity compile
```bash
cd ../SimpleDBClients
javac -d bin -cp ../SimpleDBEngine/bin:src $(find src -name "*.java")
```
Confirms the `embedded`/`network` `CreateStudentDB.java` copies still
compile with the added `create index ... using ...` statements. Running
them end-to-end requires starting the RMI server
(`simpledb.server.Startup`) first, which is outside this assignment's
required test list.

All of the above were run during development; results:
- `ParserTest`: all three `yes`.
- `CreateStudentDB`: both indexes created and populated as expected.
- `IndexRetrievalTest`: `amy, sue, kim, pat`.
- `IndexUpdateTest`: `sam` present, `joe` absent.
- `IndexSelectTest`: `A`, `A`.
- `IndexJoinTest`: matching grades printed via both the manual and
  index-scan/index-join paths.
- `IndexTypeTest`: `ALL PASS`.
- `MetadataMgrTest`: ran cleanly with distinct hash/btree costs.
- `SimpleDBClients` compiled without errors.
- Lab1's `NonEqualityScanTest` re-run as a regression check: `ALL PASS`
  (confirms this change didn't disturb non-equality predicate handling).
