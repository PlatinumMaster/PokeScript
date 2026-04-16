package ctrmap.pokescript.stage0;

import ctrmap.pokescript.CompilerExceptionData;
import ctrmap.pokescript.stage0.EffectiveLine.PreprocessorState;
import ctrmap.pokescript.util.CompilerLogger;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AntlrDirectiveProcessorTest {

    private PreprocessorState state;
    private List<CompilerExceptionData> errors;
    private TestLogger logger;

    @Before
    public void setUp() {
        state = new PreprocessorState();
        errors = new ArrayList<>();
        logger = new TestLogger();
    }

    private void process(String directive) {
        AntlrDirectiveProcessor.process(directive, state, 1, "Test.pks", logger, errors);
    }

    private void processAt(String directive, int line) {
        AntlrDirectiveProcessor.process(directive, state, line, "Test.pks", logger, errors);
    }

    // --- #define ---

    @Test
    public void define_addsToDefinedList() {
        process("define FOO");
        assertTrue("FOO should be defined", state.defined.contains("FOO"));
    }

    @Test
    public void define_multipleSymbols() {
        process("define A");
        process("define B");
        assertTrue(state.defined.contains("A"));
        assertTrue(state.defined.contains("B"));
    }

    // --- #undef ---

    @Test
    public void undef_removesFromDefinedList() {
        state.defined.add("FOO");
        process("undef FOO");
        assertFalse("FOO should be undefined", state.defined.contains("FOO"));
    }

    @Test
    public void undef_nonexistentSymbol_noError() {
        process("undef NONEXISTENT");
        assertTrue("Undefining a non-existent symbol should not produce errors",
                errors.isEmpty());
    }

    // --- #ifdef ---

    @Test
    public void ifdef_whenDefined_pushesTrue() {
        state.defined.add("FOO");
        process("ifdef FOO");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    @Test
    public void ifdef_whenNotDefined_pushesFalse() {
        process("ifdef FOO");
        assertEquals(1, state.ppStack.size());
        assertFalse(state.ppStack.peek());
    }

    // --- #ifndef ---

    @Test
    public void ifndef_whenDefined_pushesFalse() {
        state.defined.add("FOO");
        process("ifndef FOO");
        assertEquals(1, state.ppStack.size());
        assertFalse(state.ppStack.peek());
    }

    @Test
    public void ifndef_whenNotDefined_pushesTrue() {
        process("ifndef FOO");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    // --- #if ---

    @Test
    public void if_one_pushesTrue() {
        process("if 1");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    @Test
    public void if_zero_pushesFalse() {
        process("if 0");
        assertEquals(1, state.ppStack.size());
        assertFalse(state.ppStack.peek());
    }

    @Test
    public void if_definedExpression() {
        state.defined.add("FOO");
        process("if defined(FOO)");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    @Test
    public void if_definedExpression_notDefined() {
        process("if defined(FOO)");
        assertEquals(1, state.ppStack.size());
        assertFalse(state.ppStack.peek());
    }

    // --- #elif ---

    @Test
    public void elif_popsAndPushesNewValue() {
        process("if 0");
        assertFalse(state.ppStack.peek());
        process("elif 1");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    @Test
    public void elif_falseToFalse() {
        process("if 0");
        process("elif 0");
        assertEquals(1, state.ppStack.size());
        assertFalse(state.ppStack.peek());
    }

    // --- #else if ---

    @Test
    public void elseIf_popsAndPushesNewValue() {
        process("if 0");
        assertFalse(state.ppStack.peek());
        process("else if 1");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    // --- #else ---

    @Test
    public void else_flipsTopOfStack_trueToFalse() {
        process("if 1");
        assertTrue(state.ppStack.peek());
        process("else");
        assertEquals(1, state.ppStack.size());
        assertFalse(state.ppStack.peek());
    }

    @Test
    public void else_flipsTopOfStack_falseToTrue() {
        process("if 0");
        assertFalse(state.ppStack.peek());
        process("else");
        assertEquals(1, state.ppStack.size());
        assertTrue(state.ppStack.peek());
    }

    // --- #endif ---

    @Test
    public void endif_popsStack() {
        process("if 1");
        assertEquals(1, state.ppStack.size());
        process("endif");
        assertTrue(state.ppStack.isEmpty());
    }

    @Test
    public void endif_nestedConditions() {
        process("if 1");
        process("if 0");
        assertEquals(2, state.ppStack.size());
        process("endif");
        assertEquals(1, state.ppStack.size());
        process("endif");
        assertTrue(state.ppStack.isEmpty());
    }

    // --- #echo ---

    @Test
    public void echo_logsMessage() {
        process("echo Hello World");
        assertTrue("Logger should have received 'Hello World'",
                logger.infoMessages.stream().anyMatch(m -> m.contains("Hello World")));
    }

    // --- #error ---

    @Test
    public void error_addsToErrorsList() {
        process("error Bad thing happened");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).text.contains("Bad thing happened"));
    }

    @Test
    public void error_noText_defaultMessage() {
        process("error");
        assertEquals(1, errors.size());
        assertNotNull(errors.get(0).text);
    }

    // --- #pragma ---

    @Test
    public void pragma_optimizeCount_storedInPragmata() {
        process("pragma optimize_count 5");
        assertTrue(errors.isEmpty());
        assertTrue(state.pragmata.containsKey(CompilerPragma.OPTIMIZE_COUNT));
        assertEquals(5, state.pragmata.get(CompilerPragma.OPTIMIZE_COUNT).intValue());
    }

    @Test
    public void pragma_leakForDeclaration_booleanPragma() {
        process("pragma leak_for_declaration");
        assertTrue(errors.isEmpty());
        assertTrue(state.pragmata.containsKey(CompilerPragma.LEAK_FOR_DECL));
        assertTrue(state.pragmata.get(CompilerPragma.LEAK_FOR_DECL).boolValue());
    }

    @Test
    public void pragma_leakForDeclaration_explicitTrue() {
        process("pragma leak_for_declaration true");
        assertTrue(errors.isEmpty());
        assertTrue(state.pragmata.containsKey(CompilerPragma.LEAK_FOR_DECL));
        assertTrue(state.pragmata.get(CompilerPragma.LEAK_FOR_DECL).boolValue());
    }

    @Test
    public void pragma_leakForDeclaration_explicitFalse() {
        process("pragma leak_for_declaration false");
        assertTrue(errors.isEmpty());
        assertTrue(state.pragmata.containsKey(CompilerPragma.LEAK_FOR_DECL));
        assertFalse(state.pragmata.get(CompilerPragma.LEAK_FOR_DECL).boolValue());
    }

    // --- error cases ---

    @Test
    public void else_emptyStack_producesError() {
        process("else");
        assertFalse("Else on empty stack should produce an error", errors.isEmpty());
    }

    @Test
    public void endif_emptyStack_producesError() {
        process("endif");
        assertFalse("Endif on empty stack should produce an error", errors.isEmpty());
    }

    @Test
    public void elif_emptyStack_producesError() {
        process("elif 1");
        assertFalse("Elif on empty stack should produce an error", errors.isEmpty());
    }

    @Test
    public void elseIf_emptyStack_producesError() {
        process("else if 1");
        assertFalse("Else if on empty stack should produce an error", errors.isEmpty());
    }

    @Test
    public void pragma_unrecognized_producesError() {
        process("pragma nonexistent_pragma_name");
        assertFalse("Unrecognized pragma should produce an error", errors.isEmpty());
        assertTrue(errors.get(0).text.contains("Unrecognized pragma"));
    }

    // --- empty directive ---

    @Test
    public void emptyDirective_noError() {
        process("");
        assertTrue(errors.isEmpty());
    }

    @Test
    public void whitespaceOnlyDirective_noError() {
        process("   ");
        assertTrue(errors.isEmpty());
    }

    // --- error location ---

    @Test
    public void error_reportsCorrectLineNumber() {
        processAt("error test", 42);
        assertEquals(1, errors.size());
        assertEquals(42, errors.get(0).lineNumberStart);
    }

    @Test
    public void error_reportsCorrectFileName() {
        process("error test");
        assertEquals(1, errors.size());
        assertEquals("Test.pks", errors.get(0).fileName);
    }

    // --- if/else/endif full sequence ---

    @Test
    public void fullSequence_ifElseEndif() {
        process("if 1");
        assertTrue(state.ppStack.peek());
        assertTrue(state.getIsCodePassthroughEnabled());

        process("else");
        assertFalse(state.ppStack.peek());
        assertFalse(state.getIsCodePassthroughEnabled());

        process("endif");
        assertTrue(state.ppStack.isEmpty());
        assertTrue(state.getIsCodePassthroughEnabled());

        assertTrue(errors.isEmpty());
    }

    @Test
    public void fullSequence_ifdefElseEndif() {
        state.defined.add("DEBUG");
        process("ifdef DEBUG");
        assertTrue(state.ppStack.peek());
        process("else");
        assertFalse(state.ppStack.peek());
        process("endif");
        assertTrue(state.ppStack.isEmpty());
        assertTrue(errors.isEmpty());
    }

    // --- test logger ---

    private static class TestLogger extends CompilerLogger {
        final List<String> infoMessages = new ArrayList<>();
        final List<String> errorMessages = new ArrayList<>();

        @Override
        public void stdout(String text) {
            infoMessages.add(text);
        }

        @Override
        public void stderr(String text) {
            errorMessages.add(text);
        }

        @Override
        public void close() {
        }
    }
}
