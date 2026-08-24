package simpledb.parse;
import java.util.Scanner;

// Will successfully read in lines of text denoting an
// SQL expression of the form "id <opr> c" or "c <opr> id",
// where <opr> is one of =, <, <=, >, >=, !=, <>.

public class LexerTest {
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		while (sc.hasNext()) {
			String s = sc.nextLine();
			Lexer lex = new Lexer(s);
			String x; int y; String opr;
			if (lex.matchId()) {
				x = lex.eatId();
				opr = lex.eatOpr();
				y = lex.eatIntConstant();
				System.out.println(x + " " + opr + " " + y);
			}
			else {
				y = lex.eatIntConstant();
				opr = lex.eatOpr();
				x = lex.eatId();
				System.out.println(y + " " + opr + " " + x);
			}
		}
		sc.close();
	}
}
