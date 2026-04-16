parser grammar PksPreprocessorParser;

options { tokenVocab = PksPreprocessorLexer; }

@header { package ctrmap.pokescript.stage0.antlr; }

directive
    : DEFINE IDENTIFIER                                              # defineDirective
    | UNDEF IDENTIFIER                                               # undefDirective
    | IFDEF IDENTIFIER                                               # ifdefDirective
    | IFNDEF IDENTIFIER                                              # ifndefDirective
    | IF ppExpression                                                # ifDirective
    | ELIF ppExpression                                              # elifDirective
    | ELSE IF ppExpression                                           # elseIfDirective
    | ELSE                                                           # elseDirective
    | ENDIF                                                          # endifDirective
    | ECHO REST_TEXT?                                                # echoDirective
    | ERROR REST_TEXT?                                                # errorDirective
    | PRAGMA IDENTIFIER pragmaValue?                                 # pragmaDirective
    ;

pragmaValue
    : IDENTIFIER
    | INTEGER
    | TRUE
    | FALSE
    ;

ppExpression
    : ppExpression op=(STAR | SLASH | PERCENT) ppExpression           # mulExpr
    | ppExpression op=(PLUS | MINUS) ppExpression                     # addExpr
    | ppExpression op=(LSHIFT | RSHIFT) ppExpression                  # shiftExpr
    | ppExpression op=(LT | GT | LTE | GTE) ppExpression              # compExpr
    | ppExpression op=(EQ | NEQ) ppExpression                         # eqExpr
    | ppExpression AMP ppExpression                                   # bitAndExpr
    | ppExpression CARET ppExpression                                 # bitXorExpr
    | ppExpression PIPE ppExpression                                  # bitOrExpr
    | ppExpression AND ppExpression                                   # logAndExpr
    | ppExpression OR ppExpression                                    # logOrExpr
    | DEFINED LPAREN IDENTIFIER RPAREN                                # definedExpr
    | LPAREN ppExpression RPAREN                                      # parenExpr
    | op=(NOT | TILDE | MINUS) ppExpression                           # unaryExpr
    | INTEGER                                                         # intLiteral
    | TRUE                                                            # trueLiteral
    | FALSE                                                           # falseLiteral
    | IDENTIFIER                                                      # identExpr
    ;
