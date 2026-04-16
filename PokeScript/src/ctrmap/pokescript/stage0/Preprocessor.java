package ctrmap.pokescript.stage0;

import ctrmap.pokescript.CompilerExceptionData;
import ctrmap.pokescript.util.CompilerLogger;
import ctrmap.pokescript.InboundDefinition;
import ctrmap.pokescript.LangCompiler;
import ctrmap.pokescript.LangConstants;
import ctrmap.pokescript.MemberDocumentation;
import ctrmap.pokescript.stage0.content.AbstractContent;
import ctrmap.pokescript.stage0.content.DeclarationContent;
import ctrmap.pokescript.stage0.content.EnumConstantDeclarationContent;
import ctrmap.pokescript.stage1.NCompilableMethod;
import ctrmap.pokescript.stage1.NCompileGraph;
import ctrmap.pokescript.types.DataType;
import ctrmap.pokescript.types.declarers.DeclarerController;
import ctrmap.pokescript.util.Token;
import ctrmap.pokescript.util.TokenSlicer;
import ctrmap.pokescript.util.Tokenizer;
import xstandard.fs.FSFile;
import xstandard.io.base.iface.ReadableStream;
import xstandard.util.ArraysEx;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Preprocessor {

	private List<EffectiveLine> lines = new ArrayList<>();
	private List<CommentDump> comments = new ArrayList<>();
	private List<CompilerExceptionData> preprocessorErrors = new ArrayList<>();
	private Map<CompilerPragma, CompilerPragma.PragmaValue> ppPragmata = new HashMap<>();

	private CompilerLogger log;
	private LangCompiler.CompilerArguments args;

	public String contextName = "UnnamedContext";
	public NCompileGraph parentGraph;
	public NCompileGraph cg;

	public Preprocessor(FSFile file, LangCompiler.CompilerArguments args, NCompileGraph parentGraph) {
		this(file.getInputStream(), file.getName(), args);
		this.parentGraph = parentGraph;
	}

	public Preprocessor(FSFile file, LangCompiler.CompilerArguments args) {
		this(file.getInputStream(), file.getName(), args);
	}

	public Preprocessor(ReadableStream stream, String contextName, LangCompiler.CompilerArguments args) {
		log = args.logger;
		this.contextName = contextName;
		this.args = args;
		read(stream);
	}

	public LangCompiler.CompilerArguments getArgs() {
		return args;
	}
	
	public void setArgs(LangCompiler.CompilerArguments args) {
		this.args = args;
	}

	public void read(FSFile fsf) {
		read(fsf.getInputStream());
	}

	public final void read(ReadableStream stream) {
		lines.clear();
		comments.clear();
		preprocessorErrors.clear();
		ppPragmata.clear();

		try {
			String source = readStreamToString(stream);

			// Tokenize and slice into COMMENT / PREPROCESSOR_DIRECTIVE / CODE blocks
			List<Token<PreprocessorTokenizer.TokenType>> allTokens =
					Tokenizer.tokenize(source, PreprocessorTokenizer.RECOGNIZER);
			List<TokenSlicer.Block<PreprocessorTokenizer.TokenType, PreprocessorTokenizer.BlockType>> blocks =
					PreprocessorTokenizer.SLICER.slice(allTokens);

			// Build token → line-number map for accurate line tracking
			IdentityHashMap<Token<PreprocessorTokenizer.TokenType>, Integer> tokenLineMap =
					new IdentityHashMap<>();
			int ln = 1;
			for (Token<PreprocessorTokenizer.TokenType> t : allTokens) {
				tokenLineMap.put(t, ln);
				String tc = t.getContent();
				for (int ci = 0; ci < tc.length(); ci++) {
					if (tc.charAt(ci) == '\n') {
						ln++;
					}
				}
			}

			EffectiveLine.AnalysisState anlState = new EffectiveLine.AnalysisState();
			EffectiveLine.PreprocessorState ppState = new EffectiveLine.PreprocessorState();
			ppState.defined = args.preprocessorDefinitions;

			for (TokenSlicer.Block<PreprocessorTokenizer.TokenType, PreprocessorTokenizer.BlockType> block : blocks) {
				int blockLine = block.tokens.isEmpty()
						? 1
						: tokenLineMap.get(block.tokens.get(0));

				switch (block.type) {
					case COMMENT: {
						if (ppState.getIsCodePassthroughEnabled()) {
							CommentDump cd = new CommentDump();
							cd.startingLine = blockLine;
							String fullText = Token.join(block.tokens);
							cd.endLine = blockLine + countNewlines(fullText);
							cd.contents = block.tokenContentTrimmed();
							comments.add(cd);
						}
						break;
					}
					case PREPROCESSOR_DIRECTIVE: {
						// Always process directives, regardless of ppState
						// (matches original behaviour)
						String directiveText = block.tokenContentTrimmed();
						AntlrDirectiveProcessor.process(
								directiveText, ppState, blockLine,
								contextName, log, preprocessorErrors);
						break;
					}
					case CODE: {
						if (ppState.getIsCodePassthroughEnabled()) {
							String codeText = Token.join(block.tokens);
							splitCodeToLines(codeText, blockLine, anlState);
						}
						break;
					}
				}
			}

			if (!ppState.ppStack.empty()) {
				if (!lines.isEmpty()) {
					lines.get(lines.size() - 1).throwException(
							"Unclosed preprocessor condition. (Count: "
									+ ppState.ppStack.size() + ")");
				} else {
					CompilerExceptionData d = new CompilerExceptionData();
					d.fileName = contextName;
					d.lineNumberStart = ln;
					d.lineNumberEnd = ln;
					d.text = "Unclosed preprocessor condition. (Count: "
							+ ppState.ppStack.size() + ")";
					preprocessorErrors.add(d);
				}
			}

			ppPragmata = ppState.pragmata;
		} catch (IOException ex) {
			Logger.getLogger(Preprocessor.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	// ---- helpers for the new read() ------------------------------------

	private static String readStreamToString(ReadableStream stream) throws IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			char[] buf = new char[4096];
			int n;
			while ((n = reader.read(buf)) != -1) {
				sb.append(buf, 0, n);
			}
			return sb.toString();
		}
	}

	private static int countNewlines(String s) {
		int count = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\n') {
				count++;
			}
		}
		return count;
	}

	/**
	 * Splits a CODE block into {@link EffectiveLine}s using statement
	 * terminators ({@code ;  {  }  :}).  Comment and preprocessor handling
	 * is already done by the tokenizer — this method only needs to track
	 * brace depth and the colon/annotation special cases.
	 */
	private void splitCodeToLines(String code, int startLine,
								   EffectiveLine.AnalysisState state) {
		int blevel = 0;
		StringBuilder sb = new StringBuilder();
		int currentLine = startLine;
		int lineAccum = 0;
		boolean firstNonWs = true;
		boolean isAnnotation = false;

		for (int i = 0; i < code.length(); i++) {
			char c = code.charAt(i);
			sb.append(c);

			if (c == '\n') {
				lineAccum++;
				if (isAnnotation) {
					emitLine(sb.toString(), currentLine, lineAccum, state);
					currentLine += lineAccum;
					sb = new StringBuilder();
					lineAccum = 0;
					firstNonWs = true;
					isAnnotation = false;
					continue;
				}
			}

			if (firstNonWs && !Character.isWhitespace(c)) {
				firstNonWs = false;
				if (c == LangConstants.CH_ANNOT_KW_IDENTIFIER) {
					isAnnotation = true;
				}
			}

			switch (c) {
				case '(':
					blevel++;
					break;
				case ')':
					blevel--;
					break;
			}

			// Colon special case: `:` after `)` is a return-type separator,
			// not a label terminator.
			if (c == LangConstants.CH_METHOD_EXTENDS_IDENT && blevel == 0) {
				boolean allowBreak = true;
				for (int j = sb.length() - 2; j >= 0; j--) {
					char c2 = sb.charAt(j);
					if (!Character.isWhitespace(c2)) {
						if (c2 == ')') {
							allowBreak = false;
						}
						break;
					}
				}
				if (!allowBreak) {
					continue;
				}
			}

			if (!isAnnotation && blevel == 0 && isTerminator(c)) {
				emitLine(sb.toString(), currentLine, lineAccum, state);
				currentLine += lineAccum;
				sb = new StringBuilder();
				lineAccum = 0;
				firstNonWs = true;
				isAnnotation = false;
			}
		}

		// Handle remaining non-empty content
		if (sb.length() > 0 && sb.toString().trim().length() > 0) {
			emitLine(sb.toString(), currentLine, lineAccum, state);
		}
	}

	private void emitLine(String data, int startingLine, int newLineCount,
						   EffectiveLine.AnalysisState state) {
		EffectiveLine l = new EffectiveLine();
		l.startingLine = startingLine;
		l.newLineCount = newLineCount;
		l.fileName = contextName;
		l.data = data;
		l.trim();

		if (l.data.isEmpty()) {
			return;
		}

		l.analyze0(state);
		lines.add(l);
		l.analyze1(state);
	}

	public static String getStrWithoutTerminator(String s) {
		for (char term : LangConstants.COMMON_LINE_TERM) {
			if (s.endsWith(String.valueOf(term))) {
				return s.substring(0, s.length() - 1);
			}
		}
		return s;
	}

	public CommentDump getCommentBeforeLine(int line) {
		EffectiveLine lastLine = null;
		int lmax = -1;
		for (EffectiveLine l : lines) {
			if (!l.hasType(EffectiveLine.LineType.PREPROCESSOR_COMMAND)) {
				int endl = l.startingLine;
				if (endl < line) {
					if (endl < lmax) {
						continue;
					}
					lmax = endl;
					lastLine = l;
				} else {
					break;
				}
			}
		}
		int lineReq = lastLine != null ? lmax : line - 1;
		//System.err.println(contextName);
		//System.err.println("req " + lineReq + ", " + line);
		CommentDump current = null;
		for (CommentDump cd : comments) {
			if (cd.endLine >= lineReq && cd.endLine <= line) {
				if (current == null) {
					current = cd;
				}
				else if (cd.endLine > current.endLine) {
					current = cd;
				}
			}
		}
		return current;
	}

	public List<NMember> getMembers() {
		return getMembers(true);
	}

	public List<NMember> getMembers(boolean localOnly) {
		if (cg == null) {
			getCompileGraph();
		}
		if (!localOnly) {
			if (cg == null) {
				return new ArrayList<>();
			}
		}
		List<NMember> members = new ArrayList<>();
		for (EffectiveLine el : lines) {
			if (el.content.getContentType() == AbstractContent.CompilerContentType.DECLARATION_ENMCONST || (el.content.getContentType() == AbstractContent.CompilerContentType.DECLARATION && el.context != EffectiveLine.AnalysisLevel.LOCAL)) {
				if (el.content.getContentType() == AbstractContent.CompilerContentType.DECLARATION_ENMCONST) {
					EnumConstantDeclarationContent ecdc = (EnumConstantDeclarationContent) el.content;

					for (EnumConstantDeclarationContent.EnumConstant c : ecdc.constants) {
						NMember m = new NMember();
						CommentDump cmt = getCommentBeforeLine(c.line);
						if (c.type != null) {
							m.type = c.type;
						} else if (cg != null && cg.currentClass != null) {
							m.type = cg.currentClass.getTypeDef();
						} else {
							m.type = DataType.ENUM.typeDef();
						}
						m.doc = cmt != null ? new MemberDocumentation(cmt.contents) : null;
						if (localOnly) {
							m.name = c.name;
						} else {
							m.name = (cg != null && cg.currentClass != null) ? c.name : LangConstants.makePath(cg.packageName, cg.currentClass.className, c.name);
						}
						m.modifiers = ArraysEx.asList(Modifier.VARIABLE, Modifier.STATIC, Modifier.FINAL);
						members.add(m);
					}
				} else {
					DeclarationContent decCnt = (DeclarationContent) el.content;
					CommentDump cmt = getCommentBeforeLine(el.startingLine);
					NMember n = new NMember();
					n.modifiers = decCnt.declaredModifiers;
					n.type = decCnt.declaredType;
					n.doc = cmt != null ? new MemberDocumentation(cmt.contents) : null;
					if (decCnt.isMethodDeclaration()) {
						NCompilableMethod m = decCnt.getMethod();

						if (localOnly) {
							n.name = m.def.name;
						} else {
							n.name = (cg.currentClass != null) ? m.def.name : LangConstants.makePath(cg.packageName, cg.currentClass.className, m.def.name);
						}
						n.args = m.def.args;
					} else {
						if (localOnly) {
							n.name = decCnt.declaredName;
						} else {
							n.name = (cg.currentClass != null) ? decCnt.declaredName : LangConstants.makePath(cg.packageName, cg.currentClass.className, decCnt.declaredName);
						}
					}
					members.add(n);
				}
			}
		}

		if (!localOnly) {
			for (Preprocessor sub : cg.includedReaders) {
				members.addAll(sub.getMembers());
			}
		}

		return members;
	}

	public List<InboundDefinition> getDeclaredMethods() {
		List<InboundDefinition> l = new ArrayList<>();
		for (EffectiveLine el : lines) {
			if (el.content != null && el.content.getContentType() == AbstractContent.CompilerContentType.DECLARATION && el.context != EffectiveLine.AnalysisLevel.LOCAL) {
				DeclarationContent decCnt = (DeclarationContent) el.content;
				if (decCnt.isMethodDeclaration()) {
					InboundDefinition def = decCnt.getMethod().def;
					l.add(def);
				}
			}
		}
		return l;
	}

	public List<String> getDeclaredFields() {
		List<String> l = new ArrayList<>();
		for (EffectiveLine el : lines) {
			if (el.content.getContentType() == AbstractContent.CompilerContentType.DECLARATION && el.context == EffectiveLine.AnalysisLevel.GLOBAL) {
				DeclarationContent decCnt = (DeclarationContent) el.content;
				if (decCnt.isVarDeclaration()) {
					l.add(decCnt.declaredName);
				}
			}
		}
		return l;
	}

	public NCompileGraph getCompileGraph() {
		cg = new NCompileGraph(args);
		if (parentGraph != null) {
			cg.merge(parentGraph);
		}
		cg.includePaths = args.includeRoots;
		// Transfer pragmas collected during preprocessing
		if (ppPragmata != null) {
			cg.pragmata.putAll(ppPragmata);
		}

		DeclarerController declarer = new DeclarerController(cg);

		List<EffectiveLine> l = new ArrayList<>(lines);
		
		for (EffectiveLine line : l) {
			cg.currentCompiledLine = line;
			if (line.exceptions.isEmpty() && line.content != null) {
				line.content.declareToGraph(cg, declarer);
			}
		}

		List<CompilerExceptionData> exc;

		for (EffectiveLine line : l) {
			cg.currentCompiledLine = line;
			if (line.exceptions.isEmpty() && line.content != null) {
				line.content.addToGraph(cg);
				if (line.hasType(EffectiveLine.LineType.BLOCK_END) && line.context == EffectiveLine.AnalysisLevel.LOCAL) {
					cg.popBlock();
				}
			}
		}

		cg.finishCompileLoad();

		exc = collectExceptions();

		for (CompilerExceptionData d : exc) {
			log.println(CompilerLogger.LogLevel.ERROR, d.toString());
		}
		
		if (!exc.isEmpty()) {
			return null;
		}

		return cg;
	}

	public List<CompilerExceptionData> collectExceptions() {
		List<CompilerExceptionData> d = new ArrayList<>(preprocessorErrors);
		for (EffectiveLine l : new ArrayList<>(lines)) {
			d.addAll(l.getExceptionData());
		}
		return d;
	}

	public boolean isCompileSuccessful() {
		if (!preprocessorErrors.isEmpty()) {
			return false;
		}
		for (EffectiveLine l : lines) {
			if (!l.exceptions.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static boolean isTerminator(char c) {
		return isTerminator(c, false);
	}

	public static boolean isTerminator(char c, boolean allowNewLine) {
		if (allowNewLine) {
			if (c == '\n') {
				return true;
			}
		}
		for (Character chara : LangConstants.COMMON_LINE_TERM) {
			if (chara == c) {
				return true;
			}
		}
		return false;
	}

	public static BraceContent getContentInBraces(String source, int firstBraceIndex) {
		return getContentInBraces(source, firstBraceIndex, false);
	}

	public static BraceContent getContentInBraces(String source, int firstBraceIndex, boolean noNeedBraceStartEnd) {
		int braceLevel = noNeedBraceStartEnd ? 1 : 0;
		int maxIdx = source.length() - 1;
		StringBuilder sb = new StringBuilder();
		BraceContent cnt = new BraceContent();
		for (int idx = firstBraceIndex; idx < source.length(); idx++) {
			char c = source.charAt(idx);
			if (c == '(') {
				braceLevel++;
			} else if (c == ')') {
				braceLevel--;
			}
			sb.append(c);
			if (braceLevel == 0 || braceLevel == 1 && idx == maxIdx) {
				cnt.hasIntegrity = true;
				cnt.endIndex = idx + 1;
				break;
			}
		}
		cnt.content = sb.toString();
		return cnt;
	}

	public static boolean checkNameValidity(String name) {
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && !LangConstants.allowedNonAlphaNumericNameCharacters.contains(c)) {
				return false;
			}
		}
		return true;
	}

	public static char safeCharAt(String str, int idx) {
		if (idx < str.length()) {
			return str.charAt(idx);
		}
		return 0;
	}

}
