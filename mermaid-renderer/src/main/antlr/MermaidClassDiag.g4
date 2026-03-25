/**
 * Combined ANTLR grammar for Mermaid class diagrams.
 *
 * Key syntax:
 *   classDiagram
 *       class Animal {
 *           <<abstract>>
 *           +String name
 *           +speak() void
 *       }
 *       Animal <|-- Dog
 *       Animal <|-- Cat : implements
 */
grammar MermaidClassDiag;

// ═══════════════════════════════════════════════════════════════
//  Parser rules
// ═══════════════════════════════════════════════════════════════

document
    : NL* directive ( NL+ line )* NL* EOF
    ;

directive
    : CLASS_DIAG
    ;

line
    : classRelation
    | classBlockStart
    | classBlockEnd
    | memberLine
    | stereotypeLine
    | lineRest
    ;

/** Class relationship: Class1 relOp Class2 (: label)? */
classRelation
    : ID CLASS_REL ID ( COLON relLabel )?
    ;

relLabel
    : ( ID | OTHER_INLINE )+
    ;

classBlockStart
    : CLASS ID LBRACE
    ;

classBlockEnd
    : RBRACE
    ;

/** Stereotype inside class block: <<abstract>> */
stereotypeLine
    : STEREO_OPEN ( ID | OTHER_INLINE )* STEREO_CLOSE
    ;

/** Class member line: +String name, -int count, +speak() void, etc. */
memberLine
    : ( PLUS | MINUS | HASH | TILDE ) ( ID | OTHER_INLINE | LPAREN | RPAREN )+
    ;

lineRest
    : ( ID | CLASS_REL | COLON | LBRACE | RBRACE | CLASS | PLUS | MINUS | HASH
      | TILDE | LPAREN | RPAREN | STEREO_OPEN | STEREO_CLOSE | OTHER_INLINE )+
    ;

// ═══════════════════════════════════════════════════════════════
//  Lexer rules
// ═══════════════════════════════════════════════════════════════

LINE_COMMENT : '%%' ~[\r\n]* -> skip ;
WS           : [ \t]+ -> channel(HIDDEN) ;
NL           : '\r'? '\n' ;

// ── Keywords ──
CLASS_DIAG : 'classDiagram' ;
CLASS      : 'class' ;

// ── Relationship operators (longest first!) ──
CLASS_REL
    : '<|--'       // inheritance (extension)
    | '<|..'       // realization
    | '*--'        // composition
    | 'o--'        // aggregation
    | '<..>'       // bidirectional dependency
    | '..>'        // dependency
    | '-->'        // association
    | '..|>'       // realization (alt direction)
    | '..'         // dashed link
    | '--'         // link
    ;

// ── Stereotype brackets ──
STEREO_OPEN  : '<<' ;
STEREO_CLOSE : '>>' ;

// ── Punctuation ──
LBRACE : '{' ;
RBRACE : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
COLON  : ':' ;
PLUS   : '+' ;
MINUS  : '-' ;
HASH   : '#' ;
TILDE  : '~' ;

// ── Identifier ──
ID : [a-zA-Z_\u00C0-\u024F] [a-zA-Z0-9_\u00C0-\u024F]* ;

// ── Catch-all ──
OTHER_INLINE : ~[\r\n \t] ;

