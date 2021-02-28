package za.co.tari.expr;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import za.co.tari.expr.MathExprEval;

public class TestMathExprEval extends TestCase
{
	public TestMathExprEval( String testName )
	{
		super( testName );
	}

	public static Test suite()
	{
		return new TestSuite( TestMathExprEval.class );
	}

	public void testApp()
	{
		try {
			MathExprEval expr = new MathExprEval("3+7*4*2+2");
			assertTrue(expr.eval() == true);
			assertTrue(expr.calculate() == 61);
		} catch (Exception e) {
			assertTrue(false);
		}

		// this should throw an exception
		try {
			MathExprEval expr = new MathExprEval("+7*4*2+2");
			assertTrue(expr.eval() == true);
			assertTrue(expr.calculate() > 0);
		} catch (Exception e) {
			assertTrue(true);
		}

		try {
			MathExprEval expr = new MathExprEval("3+7*4*2+2");
			assertTrue(expr.eval() == true);
			assertTrue(expr.calculate() != 19);
			assertTrue(expr.calculate() != 19);
		} catch (Exception e) {
			assertTrue(true);
		}
	}
}
