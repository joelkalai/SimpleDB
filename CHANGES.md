# SimpleDB+ — Changes to the Original Code Base

Paths are under `SimpleDBEngine/src/simpledb/` unless they start with `SimpleDBClients/`.

## Lab 1: Non-equality predicates

- `parse/Lexer.java` — added `eatOpr()` to read the operators `=`, `<`, `<=`, `>`, `>=`, `!=`, `<>`.
- `parse/Parser.java`, `parse/PredParser.java` — `term()` calls `eatOpr()` instead of `eatDelim('=')`.
- `parse/BadSyntaxException.java` — added a constructor that takes an error message.
- `query/Term.java` — added the operator to a term; `isSatisfied()` compares with it; `equatesWithConstant()` and `equatesWithField()` apply to `=` only, so indexes are used only for equality.
- `parse/LexerTest.java` — tests all seven operators.
- `query/NonEqualityScanTest.java` (new) — checks each operator on int and string fields.

## Lab 2: Hash and B-tree indexes

- `parse/Lexer.java` — added keywords `using`, `hash`, `btree`.
- `parse/Parser.java`, `parse/CreateIndexData.java` — `create index ... using hash|btree` (default `hash`).
- `metadata/MetadataMgr.java`, `metadata/IndexMgr.java` — index type stored in a new `indextype` column of the index catalog.
- `metadata/IndexInfo.java` — `open()` builds a `HashIndex` or `BTreeIndex` according to the stored type; added `indexType()`.
- `index/planner/IndexUpdatePlanner.java`, `plan/BasicUpdatePlanner.java` — pass the index type when creating an index.
- `server/SimpleDB.java` — switched to `HeuristicQueryPlanner` and `IndexUpdatePlanner`.
- `test/CreateStudentDB.java`, `SimpleDBClients/src/embedded/CreateStudentDB.java`, `SimpleDBClients/src/network/CreateStudentDB.java` — create a hash index on `STUDENT.MajorId` and a B-tree index on `ENROLL.StudentId`.
- `metadata/MetadataMgrTest.java` — passes an index type.
- `index/IndexTypeTest.java` (new) — tests both index types and that only equality uses an index.

## Lab 3: ORDER BY and sorting

- `parse/Lexer.java` — added keywords `order`, `by`, `asc`, `desc`.
- `parse/Parser.java`, `parse/QueryData.java` — parse `order by field [asc|desc], ...` into a list of sort fields.
- `query/SortField.java` (new) — a field name plus a direction.
- `materialize/RecordComparator.java`, `materialize/SortPlan.java` — sort on the given fields, each ascending or descending.
- `materialize/MergeJoinPlan.java`, `materialize/GroupByPlan.java` — use ascending sort fields.
- `plan/BasicQueryPlanner.java`, `opt/HeuristicQueryPlanner.java` — add a `SortPlan` only when the query has `order by`.
- `query/Term.java` — tidied the comparison code.
- `plan/OrderByTest.java` (new) — tests ORDER BY.

## Lab 4: Join algorithms

- `opt/TablePlanner.java` — builds every applicable join (index, sort-merge, nested loops) and keeps the cheapest; index and merge joins only for equality terms.
- `opt/JoinStrategy.java` (new) — lets a test restrict the optimizer to one join algorithm.
- `plan/NestedLoopsJoinPlan.java`, `query/NestedLoopsJoinScan.java` (new) — nested-loops join that applies the join predicate itself, with no separate selection.
- `query/Predicate.java` — `joinSubPredExcluding()`, so the term a join already enforces is not re-checked.
- `materialize/MergeJoinScan.java`, `materialize/SortScan.java` — fixed restoring the saved position when a scan has ended.
- `query/Constant.java` — `equals()` handles non-`Constant` arguments.
- `opt/HeuristicQueryPlanner.java` — prints the access and join method chosen for each table.
- `materialize/SortPlan.java` — merges runs until one sorted run is left, instead of stopping at two.
- `index/query/IndexJoinScan.java` — returns no rows instead of crashing when the outer input is empty.
- `test/CreateBigStudentDB.java`, `test/JoinTest.java` (new) — larger test database; checks that every join algorithm gives the same result.

## Lab 5: Hash join and aggregates

- `materialize/HashJoinPlan.java`, `materialize/HashJoinScan.java` (new) — partition-based (grace) hash join over temporary-table partitions.
- `opt/TablePlanner.java`, `opt/JoinStrategy.java` — hash join added as a join choice.
- `parse/AggregateSpec.java` (new), `parse/Lexer.java`, `parse/Parser.java`, `parse/QueryData.java` — parse `sum`, `count`, `avg`, `min`, `max`, `group by`, and aggregates in `order by`.
- `materialize/SumFn.java`, `materialize/AvgFn.java`, `materialize/MinFn.java` (new); `materialize/CountFn.java`, `materialize/MaxFn.java`, `materialize/AggregationFn.java` — the five aggregate functions.
- `materialize/GroupByPlan.java`, `materialize/GroupByScan.java` — sort-based grouping; with no `group by`, an empty input gives 0 for `count` and `sum`.
- `plan/ResultPlanner.java` (new), `plan/BasicQueryPlanner.java`, `opt/HeuristicQueryPlanner.java` — shared planning of aggregation, sorting and projection.
- `materialize/SortPlan.java` — fixed a crash on empty input.
- `test/AggregateTest.java` (new); `test/JoinTest.java` — tests aggregates and hash join.

## Lab 6: DISTINCT and query plan display

- `materialize/DistinctPlan.java`, `materialize/DistinctScan.java` (new) — `select distinct`, built on the sort operator.
- `parse/Lexer.java`, `parse/Parser.java`, `parse/QueryData.java` — parse `distinct` and the `explain` prefix.
- `plan/Plan.java` — added `describe()` and `children()`; implemented in every plan class (`TablePlan`, `SelectPlan`, `ProjectPlan`, `ProductPlan`, `OptimizedProductPlan`, `NestedLoopsJoinPlan`, `MultibufferProductPlan`, `IndexSelectPlan`, `IndexJoinPlan`, `SortPlan`, `MaterializePlan`, `MergeJoinPlan`, `HashJoinPlan`, `GroupByPlan`), with `fieldName()` added to `metadata/IndexInfo.java` for the index descriptions.
- `plan/PlanPrinter.java`, `plan/ExplainPlan.java`, `plan/ExplainScan.java` (new); `plan/Planner.java` — `explain <query>` returns the chosen plan tree with block and row estimates.
- `plan/ResultPlanner.java` — skips the sort when `order by` repeats the `group by` fields.
- `test/SimpleIJ.java`, `SimpleDBClients/src/SimpleIJ.java` — accept `explain`.
- `test/DistinctTest.java`, `test/ExplainTest.java` (new) — tests DISTINCT and EXPLAIN.

## Lab 7: Integration

- `tx/Transaction.java`, `materialize/TempTable.java`, `file/FileMgr.java` — temporary table files are released when a transaction ends, so sort-heavy queries no longer run out of file handles.
- `test/IntegrationTest.java` (new) — runs a mixed query suite under every join algorithm and through the embedded JDBC driver.
- `SimpleDBClients/src/embedded/PlusDemo.java`, `SimpleDBClients/src/network/PlusDemo.java` (new) — JDBC demos of the new SQL.
- `test/JoinExperiments.java` (new) — runs the timing experiments for the report.
