# Supporting Multiple Index Types in SimpleDB

## Overview

The goal for this part of the project was to let a single database hold more
than one kind of index at the same time, specifically a hash index on one
attribute and a B-tree index on another, and to have the query planner
actually use whichever index exists. This follows requirement 4 of the
assignment at
https://www.comp.nus.edu.sg/~tankl/cs3223/project/index.htm

The starting code already contained working `HashIndex` and `BTreeIndex`
classes, plus index-aware query and update planners, but none of it was
reachable in practice. Two things were in the way. First, `SimpleDB.java`
wired up the basic, index-blind planners, so the planner never consulted an
index even when one existed. Second, `IndexInfo.open()` was hardcoded to
return a `HashIndex`, with the B-tree branch left as a commented-out line,
and the `idxcat` catalog had nowhere to record which type an index was
meant to be. So even after turning the planners on, every index would still
have come back as a hash index.

The changes below address both problems. The short version is: record an
index type in the catalog, thread that type value from the SQL parser down
to the point where the index object is constructed, and switch on it there.

## Changes

The overall flow is: a new `using hash` / `using btree` clause is parsed on
`create index`, the chosen type is carried through the metadata API, stored
in the `idxcat` catalog, and read back when an index is opened so the right
implementation class is constructed. Turning on the index-aware planners is
what makes any of it visible to queries.

| File to change | Changes |
|---|---|
| `simpledb/parse/Lexer.java` | Added keywords `using`, `hash`, and `btree` to the keyword list in `initKeywords()`. |
| `simpledb/parse/Parser.java` | In `createIndex()`, added code to handle an optional `using hash` / `using btree` clause after the field list. Defaults to `"hash"` when the clause is omitted, then passes the type into the `CreateIndexData` constructor. |
| `simpledb/parse/CreateIndexData.java` | Added an `idxtype` field and an `indexType()` accessor. Constructor now takes 4 arguments instead of 3. |
| `simpledb/metadata/MetadataMgr.java` | `createIndex()` gained an `idxtype` parameter, forwarded straight to `IndexMgr`. |
| `simpledb/metadata/IndexMgr.java` | Added a 4th column `indextype` to the `idxcat` catalog schema. `createIndex()` takes an `idxtype` argument and writes it to that column (null defaults to `"hash"`). `getIndexInfo()` reads the column back and passes it into the `IndexInfo` constructor. |
| `simpledb/metadata/IndexInfo.java` | Added an `idxtype` field from a new constructor parameter, normalised so anything other than `"btree"` (case-insensitive) becomes `"hash"`. `open()` now returns a `BTreeIndex` or `HashIndex` depending on the type, and `blocksAccessed()` calls the matching `searchCost()`. Added an `indexType()` accessor and `HASH` / `BTREE` constants. |
| `simpledb/index/planner/IndexUpdatePlanner.java` | `executeCreateIndex()` now passes `data.indexType()` through to `mdm.createIndex(...)`. |
| `simpledb/plan/BasicUpdatePlanner.java` | Same one-line change to `executeCreateIndex()`. Not on the active path, updated only so it still compiles. |
| `simpledb/server/SimpleDB.java` | Constructor now builds `HeuristicQueryPlanner` + `IndexUpdatePlanner` instead of the index-blind `BasicQueryPlanner` + `BasicUpdatePlanner` (basic pair left in place, commented out). Without this the planner never consults an index. |
| `simpledb/test/CreateStudentDB.java` | Added two `create index` statements, each before its table's insert loop (entries are only populated as rows are inserted): `create index idx_majorid on STUDENT(MajorId) using hash` and `create index idx_studentid on ENROLL(StudentId) using btree`. |
| `SimpleDBClients/src/embedded/CreateStudentDB.java`, `SimpleDBClients/src/network/CreateStudentDB.java` | Same two `create index` statements as the engine copy, kept in sync. |
| `simpledb/metadata/MetadataMgrTest.java` | The two `mdm.createIndex(...)` calls now pass an explicit type, `"hash"` for one index and `"btree"` for the other. |
| `simpledb/index/IndexTypeTest.java` (new) | End-to-end test: builds a table with a hash index on one column and a btree index on another, then checks that `indexType()` reports each correctly, that an equality query is routed through the index, and that a range query on an indexed column is not. |
| `.gitignore` | Added `indextypetest/`, the scratch database directory `IndexTypeTest` creates. |


## AI usage

AI assistance was used in two places. It was used to write the test program
`IndexTypeTest.java` and to help draft this report. All of the changes to
the engine itself, the design decisions behind them, and the manual testing
were done without it.
