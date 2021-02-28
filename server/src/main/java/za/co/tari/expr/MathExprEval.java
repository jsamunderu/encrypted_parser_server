package za.co.tari.expr;
import java.util.Stack;

//Based on the Shunting Yard Algorithm: https://en.wikipedia.org/wiki/Shunting-yard_algorithm
public class MathExprEval {
	private String expr;
	private Stack<Double> numberTokens;
	private Stack<Character> funcTokens;

	public MathExprEval(String expr) {
		this.expr = expr;
		this.numberTokens = new  Stack<Double>();
		this.funcTokens = new Stack<Character>();
	}

	public boolean eval() {
		StringBuilder digit = new StringBuilder();
		for (int i = 0; i < expr.length(); i++) {
			if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.') {
				digit.append(expr.charAt(i));
			} else if (expr.charAt(i) == '+' || expr.charAt(i) == '-' || expr.charAt(i) == '*' || expr.charAt(i) == '/') {
				if (digit.length() > 0) {
					Double d = Double.parseDouble(digit.toString());
					numberTokens.push(d);
					digit.setLength(0);
				}
				funcTokens.push(new Character(expr.charAt(i)));
			} else if (expr.charAt(i) == ' ') {
				if (digit.length() > 0) {
					Double d = Double.parseDouble(digit.toString());
					numberTokens.push(d);
					digit.setLength(0);
				}
			} else {
				return false;
			}
		}
		Double d = Double.parseDouble(digit.toString());
		numberTokens.push(d);

		return true;
	}

	public double calculate() {
		Stack<Double> nmbTkns = new Stack<Double>();
		Stack<Character> fnTkns = new Stack<Character>();

		nmbTkns.push(numberTokens.pop());
		while (numberTokens.size() > 0 || funcTokens.size() > 0) {
			char c = funcTokens.pop();
			if (c == '*') {
				double a = numberTokens.pop();
				double b = nmbTkns.pop();
				nmbTkns.add(new Double(a*b));
			} else if (c == '/') {
				double a = numberTokens.pop();
				double b = nmbTkns.pop();
				nmbTkns.add(new Double(b/a));
			} else if (c == '+' || c == '-') {
				fnTkns.add(new Character(c));
				nmbTkns.add(numberTokens.pop());
			}
		}
		
		while (fnTkns.size() > 0) {
			char c = fnTkns.pop();
			double a = nmbTkns.pop();
			double b = nmbTkns.pop();
			if (c == '+') {
				nmbTkns.add(a + b);
			} else {
				nmbTkns.add(a - b);
			}
		}
		if (nmbTkns.size() > 1) {
			throw new IllegalStateException("Bad expression");
		}
		return nmbTkns.pop();
	}
}
