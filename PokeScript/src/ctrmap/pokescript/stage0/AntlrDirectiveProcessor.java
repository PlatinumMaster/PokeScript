package ctrmap.pokescript.stage0;

import ctrmap.pokescript.CompilerExceptionData;
import ctrmap.pokescript.stage0.EffectiveLine.PreprocessorState;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorLexer;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorParser;
import ctrmap.pokescript.stage0.antlr.PksPreprocessorParserBaseVisitor;
import ctrmap.pokescript.util.CompilerLogger;
import org.antlr.v4.runtime.*;

import java.util.List;

/**
 * Processes preprocessor directives using an ANTLR-generated parser,
 * replacing the old {@code TextPreprocessorCommandReader}.
 *
 * Each call handles a single directive (the text between {@code #} and the
 * terminating newline, with both stripped).
 */
public class AntlrDirectiveProcessor {

	/**
	 * Parse and execute a single preprocessor directive.
	 *
	 * @param directiveText trimmed directive content (without leading '#' or trailing newline)
	 * @param state         current preprocessor state (definitions, ppStack, pragmata)
	 * @param lineNumber    source line number for error messages
	 * @param fileName      source file name for error messages
	 * @param log           compiler logger
	 * @param errors        list to collect compilation errors into
	 */
	public static void process(String directiveText, PreprocessorState state,
							   int lineNumber, String fileName,
							   CompilerLogger log, List<CompilerExceptionData> errors) {
		directiveText = directiveText.trim();
		if (directiveText.isEmpty()) {
			return;
		}

		// Strip backslash-newline continuations before lexing
		directiveText = directiveText.replace("\\\n", " ");

		CharStream input = CharStreams.fromString(directiveText);
		PksPreprocessorLexer lexer = new PksPreprocessorLexer(input);
		lexer.removeErrorListeners();

		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PksPreprocessorParser parser = new PksPreprocessorParser(tokens);
		parser.removeErrorListeners();
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
									int line, int charPositionInLine, String msg,
									RecognitionException e) {
				addError(errors, fileName, lineNumber, "Preprocessor syntax error: " + msg);
			}
		});

		PksPreprocessorParser.DirectiveContext ctx = parser.directive();
		new DirectiveVisitor(state, lineNumber, fileName, log, errors).visit(ctx);
	}

	private static void addError(List<CompilerExceptionData> errors,
								  String fileName, int lineNumber, String text) {
		CompilerExceptionData d = new CompilerExceptionData();
		d.fileName = fileName;
		d.lineNumberStart = lineNumber;
		d.lineNumberEnd = lineNumber;
		d.text = text;
		errors.add(d);
	}

	// ---- visitor --------------------------------------------------------

	private static class DirectiveVisitor extends PksPreprocessorParserBaseVisitor<Void> {

		private final PreprocessorState state;
		private final int lineNumber;
		private final String fileName;
		private final CompilerLogger log;
		private final List<CompilerExceptionData> errors;

		DirectiveVisitor(PreprocessorState state, int lineNumber,
						 String fileName, CompilerLogger log,
						 List<CompilerExceptionData> errors) {
			this.state = state;
			this.lineNumber = lineNumber;
			this.fileName = fileName;
			this.log = log;
			this.errors = errors;
		}

		@Override
		public Void visitDefineDirective(PksPreprocessorParser.DefineDirectiveContext ctx) {
			state.defined.add(ctx.IDENTIFIER().getText());
			return null;
		}

		@Override
		public Void visitUndefDirective(PksPreprocessorParser.UndefDirectiveContext ctx) {
			state.defined.remove(ctx.IDENTIFIER().getText());
			return null;
		}

		@Override
		public Void visitIfdefDirective(PksPreprocessorParser.IfdefDirectiveContext ctx) {
			state.ppStack.push(state.defined.contains(ctx.IDENTIFIER().getText()));
			return null;
		}

		@Override
		public Void visitIfndefDirective(PksPreprocessorParser.IfndefDirectiveContext ctx) {
			state.ppStack.push(!state.defined.contains(ctx.IDENTIFIER().getText()));
			return null;
		}

		@Override
		public Void visitIfDirective(PksPreprocessorParser.IfDirectiveContext ctx) {
			long val = new PreprocessorExpressionEvaluator(state).visit(ctx.ppExpression());
			state.ppStack.push(val != 0);
			return null;
		}

		@Override
		public Void visitElifDirective(PksPreprocessorParser.ElifDirectiveContext ctx) {
			if (state.ppStack.empty()) {
				addError(errors, fileName, lineNumber,
						"elif not expected - no condition is active.");
				return null;
			}
			state.ppStack.pop();
			long val = new PreprocessorExpressionEvaluator(state).visit(ctx.ppExpression());
			state.ppStack.push(val != 0);
			return null;
		}

		@Override
		public Void visitElseIfDirective(PksPreprocessorParser.ElseIfDirectiveContext ctx) {
			if (state.ppStack.empty()) {
				addError(errors, fileName, lineNumber,
						"else if not expected - no condition is active.");
				return null;
			}
			state.ppStack.pop();
			long val = new PreprocessorExpressionEvaluator(state).visit(ctx.ppExpression());
			state.ppStack.push(val != 0);
			return null;
		}

		@Override
		public Void visitElseDirective(PksPreprocessorParser.ElseDirectiveContext ctx) {
			if (state.ppStack.empty()) {
				addError(errors, fileName, lineNumber,
						"Else not expected - no condition is active.");
				return null;
			}
			state.ppStack.push(!state.ppStack.pop());
			return null;
		}

		@Override
		public Void visitEndifDirective(PksPreprocessorParser.EndifDirectiveContext ctx) {
			if (state.ppStack.empty()) {
				addError(errors, fileName, lineNumber,
						"EndIf not expected - remove this token.");
				return null;
			}
			state.ppStack.pop();
			return null;
		}

		@Override
		public Void visitEchoDirective(PksPreprocessorParser.EchoDirectiveContext ctx) {
			if (ctx.REST_TEXT() != null) {
				log.println(CompilerLogger.LogLevel.INFO, ctx.REST_TEXT().getText().trim());
			}
			return null;
		}

		@Override
		public Void visitErrorDirective(PksPreprocessorParser.ErrorDirectiveContext ctx) {
			String msg = ctx.REST_TEXT() != null
					? ctx.REST_TEXT().getText().trim()
					: "Preprocessor error";
			addError(errors, fileName, lineNumber, msg);
			return null;
		}

		@Override
		public Void visitPragmaDirective(PksPreprocessorParser.PragmaDirectiveContext ctx) {
			String tag = ctx.IDENTIFIER().getText();
			String value = ctx.pragmaValue() != null ? ctx.pragmaValue().getText() : "";

			for (CompilerPragma p : CompilerPragma.values()) {
				if (p.tag.equals(tag)) {
					// Use a dummy EffectiveLine for PragmaValue's error reporting
					EffectiveLine dummy = new EffectiveLine();
					dummy.fileName = fileName;
					dummy.startingLine = lineNumber;
					dummy.newLineCount = 0;
					CompilerPragma.PragmaValue val = new CompilerPragma.PragmaValue(p, value, dummy);
					if (val.val != null) {
						state.pragmata.put(p, val);
					}
					// Collect any errors from the dummy line
					for (String ex : dummy.exceptions) {
						addError(errors, fileName, lineNumber, ex);
					}
					return null;
				}
			}
			addError(errors, fileName, lineNumber, "Unrecognized pragma: " + tag);
			return null;
		}
	}
}
