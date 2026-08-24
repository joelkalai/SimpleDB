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

All commands assume `cd SimpleDBEngine`.

### 1. Compile everything
```bash
javac -d bin $(find src -name "*.java")
```
(Or just save any file in Eclipse / `Project → Clean...` to let Eclipse rebuild `bin/`.)

### 2. Lexer — `LexerTest`
Feeds `id <opr> const` / `const <opr> id` lines and echoes back what it parsed.
```bash
printf 'a < 5\na <= 5\na > 5\na >= 5\na != 5\na <> 5\na = 5\n5 < a\n' \
  | java -cp bin simpledb.parse.LexerTest
```
Expect each line echoed back unchanged, e.g. `a <= 5`. A malformed operator
(e.g. `a << 5`) should throw `BadSyntaxException`.

### 3. Parser — `ParserTest`
Feeds full SQL statements; each should print `yes`.
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

### 4. Predicate grammar — `PredParserTest`
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

### 5. End-to-end evaluation — `ScanTest1` / `ScanTest2` (regression)
These still build equality `Term`s directly and should behave exactly as
before:
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

### 6. End-to-end evaluation of the new operators
`ScanTest1`/`ScanTest2` only exercise `=`. To confirm `<, <=, >, >=, !=, <>`
actually filter rows correctly (both int and string fields), build a
`Predicate` with the new 3-arg `Term` constructor and run it through
`SelectScan`, e.g.:
```java
Term t = new Term(new Expression("A"), "<", new Expression(new Constant(5)));
Predicate pred = new Predicate(t);
Scan s = new SelectScan(new TableScan(tx, "T", layout), pred);
```
then count/print rows via `s.next()` and compare against the expected count.
This was verified during development (10 rows, `A` = 0..9):

| Term         | Expected rows |
|--------------|---------------|
| `A<5`        | 5  (0..4)     |
| `A<=5`       | 6  (0..5)     |
| `A>5`        | 4  (6..9)     |
| `A>=5`       | 5  (5..9)     |
| `A!=5`       | 9             |
| `A<>5`       | 9             |
| `A=5`        | 1             |
| `B<'rec5'`   | 5             |
| `B!='rec5'`  | 9             |

All 9 cases passed.
