/**
 * ANTLR4 Lexer Grammar for IBM z/OS JCL (Job Control Language)
 * Designed for outline extraction (JOB, EXEC, DD, PROC, etc.)
 *
 * JCL lines are column-sensitive:
 *   Col 1-2:  // = JCL statement, //* = comment
 *   Col 3:    space = unnamed statement, name starts here
 *
 * Compatible with ANTLR 4.9.3 / Java 8.
 */
lexer grammar JCLLexer;

// ── line-level tokens (matched at column 1) ─────────────────────────
COMMENT_LINE
    : '//*' ~[\r\n]* -> channel(HIDDEN)
    ;

SLASH_SLASH
    : '//'
    ;

// ── JCL keywords (case-insensitive) ─────────────────────────────────
JOB     : J O B ;
EXEC    : E X E C ;
DD      : D D ;
PROC    : P R O C ;
PEND    : P E N D ;
SET     : S E T ;
INCLUDE : I N C L U D E ;
JCLLIB  : J C L L I B ;
OUTPUT  : O U T P U T ;
IF      : I F ;
THEN    : T H E N ;
ELSE    : E L S E ;
ENDIF   : E N D I F ;

// ── symbols ─────────────────────────────────────────────────────────
EQ      : '=' ;
COMMA   : ',' ;
LPAREN  : '(' ;
RPAREN  : ')' ;
DOT     : '.' ;
STAR    : '*' ;
AMP     : '&' ;

// ── literals ────────────────────────────────────────────────────────
STRING  : '\'' ( ~'\'' | '\'\'' )* '\'' ;
NUMBER  : [0-9]+ ;
NAME    : [A-Za-z@#$] [A-Za-z0-9@#$]* ;

// ── whitespace / newlines ───────────────────────────────────────────
NL      : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

// ── catch-all for characters we don't care about ────────────────────
ANY     : . -> skip ;

// ── case-insensitive fragments ──────────────────────────────────────
fragment A : [Aa]; fragment B : [Bb]; fragment C : [Cc]; fragment D : [Dd];
fragment E : [Ee]; fragment F : [Ff]; fragment G : [Gg]; fragment H : [Hh];
fragment I : [Ii]; fragment J : [Jj]; fragment K : [Kk]; fragment L : [Ll];
fragment M : [Mm]; fragment N : [Nn]; fragment O : [Oo]; fragment P : [Pp];
fragment Q : [Qq]; fragment R : [Rr]; fragment S : [Ss]; fragment T : [Tt];
fragment U : [Uu]; fragment V : [Vv]; fragment W : [Ww]; fragment X : [Xx];
fragment Y : [Yy]; fragment Z : [Zz];

