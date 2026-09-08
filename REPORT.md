# CS3223 — Supporting Non-Equality Predicates in SimpleDB

**Repository:** `SimpleDB` · **Branch:** `lab1_ben`

Stock SimpleDB accepts only equality in a `WHERE` clause: its grammar defines
`<Term> := <Expression> = <Expression>`. This change extends the engine to
support `<`, `<=`, `>`, `>=`, `!=` and `<>`, for both integer and string
fields, in queries as well as `UPDATE` and `DELETE` statements.

---

## 1. High-level steps

Adding an operator to SimpleDB touches three layers, in this order:

1. **Lexer** — teach the scanner to recognise a comparison operator as a unit.
   The complication is that `<=`, `>=`, `!=` and `<>` arrive from
   `StreamTokenizer` as *two separate tokens*, so a single `matchDelim()` call
   can never see them whole.
2. **Parser** — change the `<Term>` grammar rule so it reads whatever operator
   the lexer returns instead of insisting on `=`, and carry that operator into
   the `Term` object.
3. **Query execution** — make `Term` evaluate its stored operator rather than
   always testing for equality.

Two further concerns fall out of the above and are easy to miss:

4. **Query planner** — `Term.equatesWithConstant()` and
   `equatesWithField()` answer the question "is this term of the form `F = c`?".
   The planner uses them to justify index lookups and distinct-value estimates.
   They must now return `null` for any non-equality term, or the optimiser will
   treat `gradyear > 2020` as though it were `gradyear = 2020`.
5. **Type safety** — the ordering operators need `Constant.compareTo()`, which
   assumes both operands hold the same type. Equality must keep using
   `Constant.equals()`, which tolerates a type mismatch by returning `false`,
   so that pre-existing behaviour is preserved.

The grammar changes from

```
<Term> := <Expression> = <Expression>
```

to

```
<Term> := <Expression> <Operator> <Expression>
<Operator> := = | < | <= | > | >= | != | <>
```

---

## 2. Files changed

| File to change | Changes |
|---|---|
| `simpledb/parse/Lexer.java` | Added `eatOpr()`, which recognises a comparison operator and returns it as a string. It uses one token of lookahead to tell single-character operators (`<`, `>`, `=`) from two-character ones (`<=`, `>=`, `!=`, `<>`), and leaves the scanner positioned on the token *following* the operator, exactly as the other `eatXxx()` methods do. Rejects a stray `!` not followed by `=`. |
| `simpledb/parse/BadSyntaxException.java` | Added a `BadSyntaxException(String message)` constructor so `eatOpr()` can report *why* parsing failed. Stock SimpleDB throws this exception with no message at all. |
| `simpledb/parse/Parser.java` | `term()` now calls `lex.eatOpr()` instead of the hardcoded `lex.eatDelim('=')`, and passes the operator to the new `Term` constructor. The `SET` clause in `modify()` is deliberately left on `eatDelim('=')` — `update t set a = 5` is an assignment, not a comparison. |
| `simpledb/parse/PredParser.java` | Same one-line change to its `term()`, so the standalone predicate-grammar harness stays consistent with the real parser. |
| `simpledb/query/Term.java` | Added a `String opr` field and a `Term(Expression, String, Expression)` constructor; the original two-argument constructor now delegates to it with `"="`, so existing code that builds equality terms directly needs no change. `isSatisfied()` delegates to a new private `compare()` helper that switches on the operator. `equatesWithConstant()` and `equatesWithField()` return `null` unless the operator is `=`. `reductionFactor()`'s constant-vs-constant branch goes through `compare()`. `toString()` prints the real operator. |
| `simpledb/parse/LexerTest.java` | Generalised from a hardcoded `id = c` pattern to use `eatOpr()`, so it exercises all seven operators. |
| `simpledb/query/NonEqualityScanTest.java` *(new)* | Test program that builds `Term` objects with each operator and runs them through a real `SelectScan`, checking row counts for both an integer and a string field. |

### Not changed (and why)

`plan/SelectPlan.java`, `opt/TablePlanner.java` and the index planners are
untouched. Non-equality predicates are evaluated row-by-row by `SelectScan`,
which is correct but not index-accelerated. Supporting index range scans was
outside the scope of the assignment's stated file list.

---

## 3. Sample queries now supported

Against the standard `studentdb` dataset.

**Integer comparison**

```sql
select sname, gradyear from student where gradyear >= 2021
```
```
      sname gradyear
--------------------
        joe     2021
        max     2022
        sue     2022
        art     2021
        lee     2021
```

**String comparison** — ordering, not merely equality

```sql
select sname from student where sname > 'max'
```
```
      sname
-----------
        sue
        pat
```

`sname >= 'max'` additionally returns `max`, confirming the boundary is handled
correctly and that comparison is genuinely lexicographic.

**Join on a non-equality predicate**

```sql
select sname, grade from student, enroll where sid < studentid
```
returns 12 rows — every (student, enrolment) pair whose student id is lower
than the enrolment's student id.

```sql
select sname, prof, yearoffered from student, section
where gradyear != yearoffered and sname = 'pat'
```
```
      sname     prof yearoffered
--------------------------------
        pat   turing        2018
        pat einstein        2017
        pat   brando        2018
```
Pat graduated in 2019 and two of the five sections ran in 2019, so three
sections remain — an inequality join combined with an equality filter.

**Both spellings of not-equal**

```sql
select sname from student where sname != 'joe'
select sname from student where sname <> 'joe'
```
Both return the same eight rows.

**Inequalities in update and delete**

```sql
update student set majorid = 30 where gradyear > 2021    -- 2 records processed
delete from student where sid > 7                        -- 2 records processed
```

---

## 4. Testing

Testing was layered, because each of SimpleDB's existing test programs
exercises a different stage of the pipeline and most of them do **not** check
that query results are correct.

| Layer | Program | What it establishes |
|---|---|---|
| Tokenising | `TokenizerTest` | Shows `<=` arriving as two separate tokens — the problem `eatOpr()` solves |
| Lexing | `LexerTest` | `eatOpr()` returns the right operator and rejects malformed ones (`a << 5`, `a ! 5`) |
| Grammar | `PredParserTest`, `ParserTest` | Statements containing the new operators parse — syntax only, no results |
| Execution | `ScanTest1`, `ScanTest2` | Regression: pre-existing equality behaviour is unchanged |
| Execution | `NonEqualityScanTest` | Each operator returns the correct row count, for an int and a string field |
| End-to-end | `SimpleIJ` | Full SQL strings against `studentdb` return correct rows |

Coverage confirmed through `SimpleIJ`: all seven operators on an integer field
and all seven on a string field; the four predicates named in the assignment
brief; the constant appearing on the left-hand side (`2021 <= gradyear`); two
inequalities conjoined with `AND`; an equality join combined with an inequality
filter; and inequalities in `UPDATE` and `DELETE`.

Regression cases that must still be rejected — `select *`, `count(...)`, `>>`,
and a bare `!` — all continue to raise `BadSyntaxException`.

### Known limitation

Comparing operands of different types with an ordering operator, such as
`where sid < 'joe'`, throws a `NullPointerException` from
`Constant.compareTo()`, since no ordering between an integer and a string is
defined. Equality is unaffected: `where sid = 'joe'` correctly returns zero
rows, and `where sid != 'joe'` returns every row, because `Constant.equals()`
treats a type mismatch as "not equal".

---

## 5. Declaration of AI tool use

All code implementation in this assignment was carried out using AI assistance.
Planning and design decisions were made by the team.

Specifically, the team decided the approach before any code was written: which
files needed to change, how the operator should be threaded from the lexer
through to `Term`, and how each layer should be tested. AI tools were then used
to write the implementation for those files, to review the resulting code, and
to help construct the test cases and their expected results.

Two outcomes of that review are worth recording, since they went beyond
transcription. The first was the observation that `equatesWithConstant()` and
`equatesWithField()` must be guarded — without it the query planner would
silently misread an inequality as an exact-match opportunity. The second was
identifying a regression in which routing equality through
`Constant.compareTo()` caused mixed-type comparisons to throw where the
original engine returned `false`; this was corrected by keeping `=`, `!=` and
`<>` on `Constant.equals()`.

All AI-produced code was reviewed, compiled and tested by the team before being
committed.
