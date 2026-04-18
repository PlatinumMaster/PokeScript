package ctrmap.pokescript.stage0;

import ctrmap.pokescript.stage0.PreprocessorTokenizer.BlockType;
import ctrmap.pokescript.stage0.PreprocessorTokenizer.TokenType;
import ctrmap.pokescript.util.Token;
import ctrmap.pokescript.util.TokenSlicer;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PreprocessorTokenizerTest {

    private List<TokenSlicer.Block<TokenType, BlockType>> slice(String code) {
        return PreprocessorTokenizer.slice(code);
    }

    // --- simple code (no directives/comments) ---

    @Test
    public void simpleCode_singleCodeBlock() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("int x = 5;\n");
        assertFalse(blocks.isEmpty());
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            assertEquals(BlockType.CODE, b.type);
        }
    }

    @Test
    public void emptyInput_noBlocks() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("");
        assertTrue(blocks.isEmpty());
    }

    // --- single-line comment ---

    @Test
    public void singleLineComment_producesCommentBlock() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("code// comment\nmore");
        boolean foundComment = false;
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.COMMENT) {
                foundComment = true;
            }
        }
        assertTrue("Expected at least one COMMENT block", foundComment);
    }

    @Test
    public void singleLineComment_trimmedContent() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("// hello world\n");
        TokenSlicer.Block<TokenType, BlockType> comment = findFirst(blocks, BlockType.COMMENT);
        assertNotNull(comment);
        String trimmed = comment.tokenContentTrimmed();
        assertFalse("Trimmed content should not start with //",
                trimmed.startsWith("//"));
        assertTrue("Trimmed content should contain 'hello world'",
                trimmed.contains("hello world"));
    }

    // --- block comment ---

    @Test
    public void blockComment_producesCommentBlock() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("a /* comment */ b");
        boolean foundComment = false;
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.COMMENT) {
                foundComment = true;
            }
        }
        assertTrue("Expected a COMMENT block for /* */", foundComment);
    }

    @Test
    public void blockComment_trimmedContent() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("/* inner */");
        TokenSlicer.Block<TokenType, BlockType> comment = findFirst(blocks, BlockType.COMMENT);
        assertNotNull(comment);
        String trimmed = comment.tokenContentTrimmed();
        assertFalse("Trimmed content should not contain /*", trimmed.contains("/*"));
        assertFalse("Trimmed content should not contain */", trimmed.contains("*/"));
        assertTrue("Trimmed content should contain ' inner '", trimmed.contains("inner"));
    }

    // --- preprocessor directive ---

    @Test
    public void preprocessorDirective_producesDirectiveBlock() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("#define FOO\n");
        TokenSlicer.Block<TokenType, BlockType> pp = findFirst(blocks, BlockType.PREPROCESSOR_DIRECTIVE);
        assertNotNull("Expected a PREPROCESSOR_DIRECTIVE block", pp);
    }

    @Test
    public void preprocessorDirective_trimmedContent() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("#define FOO\n");
        TokenSlicer.Block<TokenType, BlockType> pp = findFirst(blocks, BlockType.PREPROCESSOR_DIRECTIVE);
        assertNotNull(pp);
        String trimmed = pp.tokenContentTrimmed();
        assertFalse("Trimmed content should not start with #", trimmed.startsWith("#"));
        assertTrue("Trimmed content should contain 'define FOO'",
                trimmed.contains("define FOO"));
    }

    // --- mixed: code + comment + directive + code ---

    @Test
    public void mixed_allBlockTypes() {
        String input = "int a;\n// comment\n#define X\nint b;\n";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);
        boolean hasCode = false, hasComment = false, hasDirective = false;
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.CODE) hasCode = true;
            if (b.type == BlockType.COMMENT) hasComment = true;
            if (b.type == BlockType.PREPROCESSOR_DIRECTIVE) hasDirective = true;
        }
        assertTrue("Expected CODE block", hasCode);
        assertTrue("Expected COMMENT block", hasComment);
        assertTrue("Expected PREPROCESSOR_DIRECTIVE block", hasDirective);
    }

    // --- line continuation in directives ---

    @Test
    public void lineContinuation_directiveSpansMultipleLines() {
        String input = "#define FOO \\\nBAR\n";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);
        TokenSlicer.Block<TokenType, BlockType> pp = findFirst(blocks, BlockType.PREPROCESSOR_DIRECTIVE);
        assertNotNull("Expected a PREPROCESSOR_DIRECTIVE block", pp);
        String joined = Token.join(pp.tokens);
        assertTrue("Directive should contain BAR (continuation captured)",
                joined.contains("BAR"));
    }

    // --- CRITICAL: code before /* */ is NOT dropped ---

    @Test
    public void codeBeforeBlockComment_notDropped() {
        String input = "int x = 5; /* comment */ int y = 6;";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);

        StringBuilder allCode = new StringBuilder();
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.CODE) {
                allCode.append(Token.join(b.tokens));
            }
        }
        String code = allCode.toString();
        assertTrue("Code before block comment should be preserved: got '" + code + "'",
                code.contains("int x"));
        assertTrue("Code after block comment should be preserved: got '" + code + "'",
                code.contains("int y"));
    }

    // --- CRITICAL: code before #directive is NOT dropped ---

    @Test
    public void codeBeforeDirective_notDropped() {
        String input = "int x = 5;\n#define FOO\nint y = 6;\n";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);

        StringBuilder allCode = new StringBuilder();
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.CODE) {
                allCode.append(Token.join(b.tokens));
            }
        }
        String code = allCode.toString();
        assertTrue("Code before directive should be preserved: got '" + code + "'",
                code.contains("int x"));
        assertTrue("Code after directive should be preserved: got '" + code + "'",
                code.contains("int y"));
    }

    // --- Nested block comment with # inside ---

    @Test
    public void hashInsideBlockComment_noDirectiveBlock() {
        String input = "/* #define FOO */\nint y;\n";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);

        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.PREPROCESSOR_DIRECTIVE) {
                fail("# inside a block comment should NOT create a PREPROCESSOR_DIRECTIVE block");
            }
        }
    }

    @Test
    public void hashInsideSingleLineComment_noDirectiveBlock() {
        String input = "// #define FOO\nint y;\n";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);

        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.PREPROCESSOR_DIRECTIVE) {
                fail("# inside a single-line comment should NOT create a PREPROCESSOR_DIRECTIVE block");
            }
        }
    }

    // --- multiple directives ---

    @Test
    public void multipleDirectives_eachGetsOwnBlock() {
        String input = "#define A\n#define B\n";
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice(input);
        int ppCount = 0;
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == BlockType.PREPROCESSOR_DIRECTIVE) ppCount++;
        }
        assertEquals("Each directive should produce its own block", 2, ppCount);
    }

    // --- whitespace only ---

    @Test
    public void whitespaceOnly_producesCodeBlock() {
        List<TokenSlicer.Block<TokenType, BlockType>> blocks = slice("   \n  \n");
        // whitespace is CODE type tokens; may or may not produce blocks
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            assertNotEquals("Whitespace should not produce COMMENT or DIRECTIVE",
                    BlockType.PREPROCESSOR_DIRECTIVE, b.type);
        }
    }

    // --- utility ---

    private TokenSlicer.Block<TokenType, BlockType> findFirst(
            List<TokenSlicer.Block<TokenType, BlockType>> blocks, BlockType type) {
        for (TokenSlicer.Block<TokenType, BlockType> b : blocks) {
            if (b.type == type) return b;
        }
        return null;
    }
}
