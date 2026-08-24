# Non-Equality Predicates — Changes & Testing

Implements support for `<`, `<=`, `>`, `>=`, `!=`, and `<>` in `WHERE` clauses
(SimpleDB previously only supported `=`), per the assignment at
https://www.comp.nus.edu.sg/~tankl/cs3223/project/non-equality.htm

## Files changed

### `SimpleDBEngine/src/simpledb/parse/Lexer.java`
Added `eatOpr()`, which recognizes a comparison token and, using one token of
lookahead, distinguishes single-character operators (`<`, `>`, `=`) from
two-character ones (`<=`, `>=`, `!=`, `<>`). It leaves the lexer positioned on
the token immediately following the operator, exactly like the other
`eatXxx()` methods.

### `SimpleDBEngine/src/simpledb/parse/BadSyntaxException.java`
Added a `BadSyntaxException(String message)` constructor so `eatOpr()` can
throw a descriptive error (e.g. a stray `!` not followed by `=`).

### `SimpleDBEngine/src/simpledb/parse/Parser.java`
`term()` now calls `lex.eatOpr()` instead of a hardcoded `lex.eatDelim('=')`,
and passes the operator through to `Term`:
```java
public Term term() {
   Expression lhs = expression();
   String opr = lex.eatOpr();
   Expression rhs = expression();
   return new Term(lhs, opr, rhs);
}
```
The `SET` clause in `modify()` (`update t set a = 5`) is unchanged — that's a
plain assignment, not a `WHERE`-clause comparison, so it still uses
`eatDelim('=')`.

### `SimpleDBEngine/src/simpledb/parse/PredParser.java`
Same one-line change as `Parser.term()`, in the standalone predicate-grammar
test harness.

### `SimpleDBEngine/src/simpledb/query/Term.java`
- Added a `String opr` field and a `Term(Expression lhs, String opr, Expression rhs)`
  constructor. The original `Term(Expression lhs, Expression rhs)` now
  delegates to it with `"="`, so any code that builds equality terms directly
  (e.g. `ScanTest1`/`ScanTest2`) needed no changes.
- `isSatisfied()` now delegates to a private `compare()` helper that switches
  on `opr`, using `Constant.compareTo()` (already `Comparable`, so it works
  for both int and string fields).
- `equatesWithConstant()` / `equatesWithField()` now return `null` unless
  `opr.equals("=")`. These two methods drive index selection and
  distinct-value estimation in `plan/SelectPlan.java` and
  `opt/TablePlanner.java` — without this guard, a `<`/`>`/`!=` term would be
  misread as an exact-match opportunity by the query optimizer and produce
  wrong results.
- `reductionFactor()`'s constant-vs-constant fallback branch now goes through
  `compare()` instead of a hardcoded `.equals()`.
- `toString()` prints the real operator instead of a hardcoded `=`.

### `SimpleDBEngine/src/simpledb/parse/LexerTest.java`
Generalized from a hardcoded `id = c` pattern to use `eatOpr()`, so it now
exercises all seven operators (`=`, `<`, `<=`, `>`, `>=`, `!=`, `<>`).

## Out of scope (not changed)

`opt/TablePlanner.java`, `plan/SelectPlan.java`, `IndexSelectPlan.java` —
non-equality predicates are still evaluated via `SelectScan`'s row-by-row
filter (correct results), not accelerated by an index range-scan. Extending
index-based access to non-equality predicates wasn't in the assignment's
required file list (`Lexer.java`, `Parser.java`, `query/Term.java`).

## How to test

**The key thing to understand: these tests check different layers of the
system, and most of them do NOT check that query results are correct.**
`Lexer` → `Parser`/`PredParser` → `Term` (execution) is a pipeline, and each
existing test tool only exercises one stage of it:

| Test           | What it actually exercises                                    | What it does **NOT** verify |
|----------------|-----------------------------------------------------------------|------------------------------|
| `LexerTest`    | Tokenizing: does `eatOpr()` recognize the right operator string and leave the token stream in the right place? | Whether the surrounding SQL is valid, whether any query runs |
| `ParserTest`   | Grammar acceptance: does a full SQL statement parse without throwing `BadSyntaxException`? Prints `yes`/`no`. | Whether the statement means the right thing or returns correct data — `yes` only means "this text is syntactically legal", not "this query works" |
| `PredParserTest` | Same as `ParserTest`, but scoped to just a `WHERE`-clause predicate in isolation (lighter, no `select`/`from` needed) | Same caveat as `ParserTest` — syntax only |
| `ScanTest1` / `ScanTest2` | **Actual execution**, but only of `=` — they build `Term`/`Predicate` objects directly (bypassing the parser) and run them through real `TableScan`/`SelectScan`/`ProductScan` over real inserted rows, checking the output is correct | Nothing about `<, <=, >, >=, !=, <>` — they never construct a non-equality `Term` |
| Custom scratch test (§6 below) | **Actual execution of the new operators**: builds a `Predicate` with the new 3-arg `Term(lhs, opr, rhs)` constructor and runs it through `SelectScan` against real rows, then checks the row *count* matches what the operator should produce | This is the only one of the six that actually proves `<, <=, >, >=, !=, <>` compute the right answer, for both int and string fields |

So: `LexerTest` proves tokenizing works, `ParserTest`/`PredParserTest` prove
the grammar accepts the new operators, and §6 (`ScanTest1`/`ScanTest2`
regression plus the custom row-count check) proves the operators actually
filter data correctly. All three layers need to pass for the feature to be
considered working — passing `ParserTest` alone would **not** catch a bug in
`Term.compare()`, for example, since `ParserTest` never evaluates a query.

All commands below assume `cd SimpleDBEngine`.

### 1. Compile everything
```bash
javac -d bin $(find src -name "*.java")
```
(Or just save any file in Eclipse / `Project → Clean...` to let Eclipse rebuild `bin/`.)

### 2. Lexer — `LexerTest` (tokenizing only)
Feeds `id <opr> const` / `const <opr> id` lines and echoes back what it parsed.
```bash
printf 'a < 5\na <= 5\na > 5\na >= 5\na != 5\na <> 5\na = 5\n5 < a\n' \
  | java -cp bin simpledb.parse.LexerTest
```
Expect each line echoed back unchanged, e.g. `a <= 5`. A malformed operator
(e.g. `a << 5`) should throw `BadSyntaxException`. This only confirms the
lexer recognizes the operator text — it says nothing about whether a query
using it would return correct data.

### 3. Parser — `ParserTest` (grammar acceptance only)
Feeds full SQL statements; each should print `yes`, meaning "parses without
error" — not "produces correct results".
```bash
printf \
'select a from t where b = 5
select a from t where b < 5
select a from t where b <= 5
select a from t where b > 5
select a from t where b >= 5
select a from t where b != 5
select a from t where b <> 5
select a from t where b < 5 and c > 3
delete from t where b >= 10
' | java -cp bin simpledb.parse.ParserTest
```

### 4. Predicate grammar — `PredParserTest` (grammar acceptance only)
Same caveat as `ParserTest`: `yes` means syntactically valid, not
semantically correct.
```bash
printf \
'a = 5
a < 5
a <= 5
a > 5
a >= 5
a != 5
a <> 5
a < 5 and b > 3
' | java -cp bin simpledb.parse.PredParserTest
```
Every line should print `yes`.

### 5. End-to-end evaluation — `ScanTest1` / `ScanTest2` (regression, `=` only)
These build equality `Term`s directly (not via the parser) and run them
against real inserted data, so they're a correctness check — but only for
the pre-existing `=` operator. They should behave exactly as before the
change:
```bash
java -cp bin simpledb.query.ScanTest1
java -cp bin simpledb.query.ScanTest2
```
Run these from a scratch directory (e.g. `/tmp`), since they create
`scantest1`/`scantest2` database folders in the current directory:
```bash
mkdir -p /tmp/simpledb-test && cd /tmp/simpledb-test
java -cp /path/to/SimpleDBEngine/bin simpledb.query.ScanTest1
java -cp /path/to/SimpleDBEngine/bin simpledb.query.ScanTest2
```

### 6. End-to-end evaluation of the new operators (correctness check — the important one)
`ScanTest1`/`ScanTest2` only exercise `=`, and `ParserTest`/`PredParserTest`
never run a query at all. `NonEqualityScanTest.java` (added alongside
`ScanTest1`/`ScanTest2` in `query/`) closes that gap: it builds a `Predicate`
with the new 3-arg `Term` constructor and runs it through `SelectScan`
against a real 10-row table, then checks the row count for each operator
matches expectations.
```bash
mkdir -p /tmp/simpledb-test && cd /tmp/simpledb-test
java -cp /path/to/SimpleDBEngine/bin simpledb.query.NonEqualityScanTest
```
(Rebuild first if needed: `javac -d bin -cp src src/simpledb/query/NonEqualityScanTest.java`, from `SimpleDBEngine/`.)

Against a table of 10 rows with `A` = 0..9 and `B` = `"rec0".."rec9"`:

| Term         | Expected rows | What it's checking |
|--------------|---------------|---------------------|
| `A<5`        | 5  (0..4)     | strict less-than, int field |
| `A<=5`       | 6  (0..5)     | inclusive boundary |
| `A>5`        | 4  (6..9)     | strict greater-than |
| `A>=5`       | 5  (5..9)     | inclusive boundary |
| `A!=5`       | 9             | `!=` spelling of not-equal |
| `A<>5`       | 9             | `<>` spelling of not-equal (must match `!=`) |
| `A=5`        | 1             | existing equality still works via the new `compare()` path |
| `B<'rec5'`   | 5             | ordering works on strings, not just ints |
| `B!='rec5'`  | 9             | not-equal works on strings too |

All 9 cases passed. This table is the actual proof that the feature works —
everything above it (`LexerTest`, `ParserTest`, `PredParserTest`) only shows
the syntax is accepted.
