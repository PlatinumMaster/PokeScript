lexer grammar PksPreprocessorLexer;

@header { package ctrmap.pokescript.stage0.antlr; }

DEFINE      : 'define';
UNDEF       : 'undef';
IFDEF       : 'ifdef';
IFNDEF      : 'ifndef';
ELIF        : 'elif';
ELSE        : 'else';
IF          : 'if';
ENDIF       : 'endif';
ECHO        : 'echo' -> pushMode(REST_LINE_MODE);
ERROR       : 'error' -> pushMode(REST_LINE_MODE);
PRAGMA      : 'pragma';
DEFINED     : 'defined';
TRUE        : 'true';
FALSE       : 'false';

IDENTIFIER  : [a-zA-Z_] [a-zA-Z0-9_.]*;
INTEGER     : '0' [xX] [0-9a-fA-F]+ | [0-9]+;

// Logical operators
AND         : '&&';
OR          : '||';
NOT         : '!';

// Comparison operators
EQ          : '==';
NEQ         : '!=';
LTE         : '<=';
GTE         : '>=';
LSHIFT      : '<<';
RSHIFT      : '>>';
LT          : '<';
GT          : '>';

// Arithmetic operators
PLUS        : '+';
MINUS       : '-';
STAR        : '*';
SLASH       : '/';
PERCENT     : '%';

// Bitwise operators
AMP         : '&';
PIPE        : '|';
CARET       : '^';
TILDE       : '~';

// Grouping
LPAREN      : '(';
RPAREN      : ')';

LINE_CONTINUATION : '\\' '\n' -> skip;
WS          : [ \t\r\n]+ -> skip;

mode REST_LINE_MODE;
REST_TEXT   : ~[\r\n]+;
