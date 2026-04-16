package ctrmap.pokescript.stage0;

import ctrmap.pokescript.stage0.EffectiveLine.PreprocessorState;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorLexer;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorParser;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorParserBaseVisitor;

/**
 * Evaluates preprocessor expressions (#if / #elif) using the ANTLR parse tree.
 *
 * All arithmetic is done in {@code long}. The final boolean result follows C
 * convention: nonzero is true, zero is false.
 */
public class PreprocessorExpressionEvaluator extends PksPreprocessorParserBaseVisitor<Long> {

	private final PreprocessorState state;

	public PreprocessorExpressionEvaluator(PreprocessorState state) {
		this.state = state;
	}

	// --- atoms ---

	@Override
	public Long visitDefinedExpr(PksPreprocessorParser.DefinedExprContext ctx) {
		return state.defined.contains(ctx.IDENTIFIER().getText()) ? 1L : 0L;
	}

	@Override
	public Long visitIntLiteral(PksPreprocessorParser.IntLiteralContext ctx) {
		String text = ctx.INTEGER().getText();
		if (text.length() > 2 && (text.charAt(1) == 'x' || text.charAt(1) == 'X')) {
			return Long.parseLong(text.substring(2), 16);
		}
		return Long.parseLong(text);
	}

	@Override
	public Long visitTrueLiteral(PksPreprocessorParser.TrueLiteralContext ctx) {
		return 1L;
	}

	@Override
	public Long visitFalseLiteral(PksPreprocessorParser.FalseLiteralContext ctx) {
		return 0L;
	}

	@Override
	public Long visitIdentExpr(PksPreprocessorParser.IdentExprContext ctx) {
		// Undefined identifiers evaluate to 0 (C preprocessor convention)
		return 0L;
	}

	@Override
	public Long visitParenExpr(PksPreprocessorParser.ParenExprContext ctx) {
		return visit(ctx.ppExpression());
	}

	// --- unary ---

	@Override
	public Long visitUnaryExpr(PksPreprocessorParser.UnaryExprContext ctx) {
		long val = visit(ctx.ppExpression());
		switch (ctx.op.getType()) {
			case PksPreprocessorLexer.NOT:
				return val == 0 ? 1L : 0L;
			case PksPreprocessorLexer.TILDE:
				return ~val;
			case PksPreprocessorLexer.MINUS:
				return -val;
			default:
				return val;
		}
	}

	// --- binary arithmetic ---

	@Override
	public Long visitMulExpr(PksPreprocessorParser.MulExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		long right = visit(ctx.ppExpression(1));
		switch (ctx.op.getType()) {
			case PksPreprocessorLexer.STAR:
				return left * right;
			case PksPreprocessorLexer.SLASH:
				return right != 0 ? left / right : 0L;
			case PksPreprocessorLexer.PERCENT:
				return right != 0 ? left % right : 0L;
			default:
				return 0L;
		}
	}

	@Override
	public Long visitAddExpr(PksPreprocessorParser.AddExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		long right = visit(ctx.ppExpression(1));
		switch (ctx.op.getType()) {
			case PksPreprocessorLexer.PLUS:
				return left + right;
			case PksPreprocessorLexer.MINUS:
				return left - right;
			default:
				return 0L;
		}
	}

	@Override
	public Long visitShiftExpr(PksPreprocessorParser.ShiftExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		long right = visit(ctx.ppExpression(1));
		switch (ctx.op.getType()) {
			case PksPreprocessorLexer.LSHIFT:
				return left << right;
			case PksPreprocessorLexer.RSHIFT:
				return left >> right;
			default:
				return 0L;
		}
	}

	// --- comparison ---

	@Override
	public Long visitCompExpr(PksPreprocessorParser.CompExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		long right = visit(ctx.ppExpression(1));
		switch (ctx.op.getType()) {
			case PksPreprocessorLexer.LT:
				return left < right ? 1L : 0L;
			case PksPreprocessorLexer.GT:
				return left > right ? 1L : 0L;
			case PksPreprocessorLexer.LTE:
				return left <= right ? 1L : 0L;
			case PksPreprocessorLexer.GTE:
				return left >= right ? 1L : 0L;
			default:
				return 0L;
		}
	}

	@Override
	public Long visitEqExpr(PksPreprocessorParser.EqExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		long right = visit(ctx.ppExpression(1));
		switch (ctx.op.getType()) {
			case PksPreprocessorLexer.EQ:
				return left == right ? 1L : 0L;
			case PksPreprocessorLexer.NEQ:
				return left != right ? 1L : 0L;
			default:
				return 0L;
		}
	}

	// --- bitwise ---

	@Override
	public Long visitBitAndExpr(PksPreprocessorParser.BitAndExprContext ctx) {
		return visit(ctx.ppExpression(0)) & visit(ctx.ppExpression(1));
	}

	@Override
	public Long visitBitXorExpr(PksPreprocessorParser.BitXorExprContext ctx) {
		return visit(ctx.ppExpression(0)) ^ visit(ctx.ppExpression(1));
	}

	@Override
	public Long visitBitOrExpr(PksPreprocessorParser.BitOrExprContext ctx) {
		return visit(ctx.ppExpression(0)) | visit(ctx.ppExpression(1));
	}

	// --- logical ---

	@Override
	public Long visitLogAndExpr(PksPreprocessorParser.LogAndExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		if (left == 0) {
			return 0L; // short-circuit
		}
		return visit(ctx.ppExpression(1)) != 0 ? 1L : 0L;
	}

	@Override
	public Long visitLogOrExpr(PksPreprocessorParser.LogOrExprContext ctx) {
		long left = visit(ctx.ppExpression(0));
		if (left != 0) {
			return 1L; // short-circuit
		}
		return visit(ctx.ppExpression(1)) != 0 ? 1L : 0L;
	}
}
