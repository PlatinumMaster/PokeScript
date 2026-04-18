package ctrmap.pokescript.stage0;

import ctrmap.pokescript.stage0.EffectiveLine.PreprocessorState;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorLexer;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PreprocessorExpressionEvaluatorTest {

    private PreprocessorState state;

    @Before
    public void setUp() {
        state = new PreprocessorState();
    }

    private long eval(String expr) {
        CharStream input = CharStreams.fromString(expr);
        PksPreprocessorLexer lexer = new PksPreprocessorLexer(input);
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PksPreprocessorParser parser = new PksPreprocessorParser(tokens);
        parser.removeErrorListeners();
        return new PreprocessorExpressionEvaluator(state).visit(parser.ppExpression());
    }

    // --- integer literals ---

    @Test
    public void intLiteral_zero() {
        assertEquals(0L, eval("0"));
    }

    @Test
    public void intLiteral_one() {
        assertEquals(1L, eval("1"));
    }

    @Test
    public void intLiteral_42() {
        assertEquals(42L, eval("42"));
    }

    @Test
    public void intLiteral_hexFF() {
        assertEquals(0xFFL, eval("0xFF"));
    }

    @Test
    public void intLiteral_hex1A() {
        assertEquals(0x1AL, eval("0x1A"));
    }

    @Test
    public void intLiteral_hexUpperCase() {
        assertEquals(0xFFL, eval("0XFF"));
    }

    // --- boolean literals ---

    @Test
    public void boolLiteral_true() {
        assertEquals(1L, eval("true"));
    }

    @Test
    public void boolLiteral_false() {
        assertEquals(0L, eval("false"));
    }

    // --- undefined identifier ---

    @Test
    public void undefinedIdentifier_returnsZero() {
        assertEquals(0L, eval("UNKNOWN_SYMBOL"));
    }

    // --- defined() ---

    @Test
    public void defined_whenDefined_returns1() {
        state.defined.add("FOO");
        assertEquals(1L, eval("defined(FOO)"));
    }

    @Test
    public void defined_whenNotDefined_returns0() {
        assertEquals(0L, eval("defined(BAR)"));
    }

    // --- unary operators ---

    @Test
    public void unaryNot_zero_returns1() {
        assertEquals(1L, eval("!0"));
    }

    @Test
    public void unaryNot_one_returns0() {
        assertEquals(0L, eval("!1"));
    }

    @Test
    public void unaryNot_nonZero_returns0() {
        assertEquals(0L, eval("!42"));
    }

    @Test
    public void unaryTilde_zero_returnsNeg1() {
        assertEquals(-1L, eval("~0"));
    }

    @Test
    public void unaryTilde_value() {
        assertEquals(~5L, eval("~5"));
    }

    @Test
    public void unaryMinus_positive() {
        assertEquals(-5L, eval("-5"));
    }

    @Test
    public void unaryMinus_zero() {
        assertEquals(0L, eval("-0"));
    }

    // --- arithmetic operators ---

    @Test
    public void addition() {
        assertEquals(7L, eval("3 + 4"));
    }

    @Test
    public void subtraction() {
        assertEquals(6L, eval("10 - 4"));
    }

    @Test
    public void multiplication() {
        assertEquals(15L, eval("3 * 5"));
    }

    @Test
    public void division() {
        assertEquals(3L, eval("10 / 3"));
    }

    @Test
    public void modulo() {
        assertEquals(1L, eval("10 % 3"));
    }

    @Test
    public void divisionByZero_returnsZero() {
        assertEquals(0L, eval("10 / 0"));
    }

    @Test
    public void moduloByZero_returnsZero() {
        assertEquals(0L, eval("10 % 0"));
    }

    // --- comparison operators ---

    @Test
    public void lessThan_true() {
        assertEquals(1L, eval("3 < 5"));
    }

    @Test
    public void lessThan_false() {
        assertEquals(0L, eval("5 < 3"));
    }

    @Test
    public void lessThan_equal_false() {
        assertEquals(0L, eval("5 < 5"));
    }

    @Test
    public void greaterThan_true() {
        assertEquals(1L, eval("5 > 3"));
    }

    @Test
    public void greaterThan_false() {
        assertEquals(0L, eval("3 > 5"));
    }

    @Test
    public void lessOrEqual_true_less() {
        assertEquals(1L, eval("3 <= 5"));
    }

    @Test
    public void lessOrEqual_true_equal() {
        assertEquals(1L, eval("5 <= 5"));
    }

    @Test
    public void lessOrEqual_false() {
        assertEquals(0L, eval("6 <= 5"));
    }

    @Test
    public void greaterOrEqual_true_greater() {
        assertEquals(1L, eval("5 >= 3"));
    }

    @Test
    public void greaterOrEqual_true_equal() {
        assertEquals(1L, eval("5 >= 5"));
    }

    @Test
    public void greaterOrEqual_false() {
        assertEquals(0L, eval("3 >= 5"));
    }

    @Test
    public void equal_true() {
        assertEquals(1L, eval("5 == 5"));
    }

    @Test
    public void equal_false() {
        assertEquals(0L, eval("5 == 6"));
    }

    @Test
    public void notEqual_true() {
        assertEquals(1L, eval("5 != 6"));
    }

    @Test
    public void notEqual_false() {
        assertEquals(0L, eval("5 != 5"));
    }

    // --- bitwise operators ---

    @Test
    public void bitwiseAnd() {
        assertEquals(0xF0L & 0x0FL, eval("0xF0 & 0x0F"));
    }

    @Test
    public void bitwiseOr() {
        assertEquals(0xF0L | 0x0FL, eval("0xF0 | 0x0F"));
    }

    @Test
    public void bitwiseXor() {
        assertEquals(0xFFL ^ 0x0FL, eval("0xFF ^ 0x0F"));
    }

    @Test
    public void leftShift() {
        assertEquals(1L << 4, eval("1 << 4"));
    }

    @Test
    public void rightShift() {
        assertEquals(16L >> 2, eval("16 >> 2"));
    }

    // --- logical operators ---

    @Test
    public void logicalAnd_bothTrue() {
        assertEquals(1L, eval("1 && 1"));
    }

    @Test
    public void logicalAnd_leftFalse() {
        assertEquals(0L, eval("0 && 1"));
    }

    @Test
    public void logicalAnd_rightFalse() {
        assertEquals(0L, eval("1 && 0"));
    }

    @Test
    public void logicalOr_bothFalse() {
        assertEquals(0L, eval("0 || 0"));
    }

    @Test
    public void logicalOr_leftTrue() {
        assertEquals(1L, eval("1 || 0"));
    }

    @Test
    public void logicalOr_rightTrue() {
        assertEquals(1L, eval("0 || 1"));
    }

    // --- operator precedence ---

    @Test
    public void precedence_mulOverAdd() {
        assertEquals(14L, eval("2 + 3 * 4"));
    }

    @Test
    public void precedence_notAndLogicalAnd() {
        assertEquals(1L, eval("!0 && 1"));
    }

    @Test
    public void precedence_addOverComparison() {
        assertEquals(1L, eval("2 + 3 == 5"));
    }

    // --- parentheses ---

    @Test
    public void parentheses_overridePrecedence() {
        assertEquals(20L, eval("(2 + 3) * 4"));
    }

    @Test
    public void parentheses_nested() {
        assertEquals(30L, eval("((2 + 3) * (4 + 2))"));
    }

    // --- complex expressions ---

    @Test
    public void complex_definedAndNotDefinedOrLiteral() {
        state.defined.add("FOO");
        // defined(FOO) = 1, !defined(BAR) = 1, 1 == 2 = 0
        // (1 && 1) || 0 = 1
        assertEquals(1L, eval("defined(FOO) && !defined(BAR) || 1 == 2"));
    }

    @Test
    public void complex_allFalse() {
        // defined(FOO) = 0 => 0 && anything = 0, 1 == 2 = 0 => 0 || 0 = 0
        assertEquals(0L, eval("defined(FOO) && !defined(BAR) || 1 == 2"));
    }

    @Test
    public void complex_bitwiseAndShift() {
        assertEquals((3L << 2) & 0xFL, eval("(3 << 2) & 0xF"));
    }

    @Test
    public void complex_nestedDefined() {
        state.defined.add("A");
        state.defined.add("B");
        assertEquals(1L, eval("defined(A) && defined(B)"));
    }

    @Test
    public void complex_nestedDefinedOneMissing() {
        state.defined.add("A");
        assertEquals(0L, eval("defined(A) && defined(B)"));
    }
}
