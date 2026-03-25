/**
 * Combined ANTLR grammar for Mermaid ER diagrams.
 *
 * Key syntax:
 *   erDiagram
 *       CUSTOMER ||--o{ ORDER : places
 *       ORDER {
 *           int id PK
 *           string date
 *       }
 *
 * Cardinality markers are captured as single tokens:
 *   ||  exactly one       |o / o|  zero or one
 *   }|  / |{  one or more     }o / o{  zero or more
 *
 * The connector is -- (non-identifying) or == (identifying).
 */
grammar MermaidErDiag;

// ═══════════════════════════════════════════════════════════════
//  Parser rules
// ═══════════════════════════════════════════════════════════════

document
    : NL* directive ( NL+ line )* NL* EOF
    ;

directive
    : ER_DIAG
    ;

line
    : relationship
    | entityBlockStart
    | attributeLine
    | RBRACE
    | lineRest
    ;

/** ER relationship: ENTITY1 cardinality--cardinality ENTITY2 : label */
relationship
    : ID leftCardinality connector rightCardinality ID ( COLON relLabel )?
    ;

leftCardinality
    : CARD_EXACTLY_ONE       // ||
    | CARD_ZERO_ONE_LEFT     // |o
    | CARD_ONE_MANY_LEFT     // }|
    | CARD_ZERO_MANY_LEFT    // }o
    ;

connector
    : NON_IDENT_CONN         // --
    | IDENT_CONN             // .. (dashed = non-identifying in Mermaid)
    ;

rightCardinality
    : CARD_EXACTLY_ONE       // ||
    | CARD_ZERO_ONE_RIGHT    // o|
    | CARD_ONE_MANY_RIGHT    // |{
    | CARD_ZERO_MANY_RIGHT   // o{
    ;

relLabel
    : ( ID | OTHER_INLINE )+
    ;

/** Entity block opening: ENTITY_NAME { */
entityBlockStart
    : ID LBRACE
    ;

/** Attribute line inside an entity block: type name [PK|FK|UK] */
attributeLine
    : ID ID ( ID )?       // e.g. "string isbn PK" or "int id"
    ;

lineRest
    : ( ID | COLON | LBRACE | RBRACE | OTHER_INLINE )+
    ;

// ═══════════════════════════════════════════════════════════════
//  Lexer rules
// ═══════════════════════════════════════════════════════════════

LINE_COMMENT : '%%' ~[\r\n]* -> skip ;
WS           : [ \t]+ -> channel(HIDDEN) ;
NL           : '\r'? '\n' ;

// ── Keyword ──
ER_DIAG : 'erDiagram' ;

// ── Cardinality markers (left side = faces connector from left) ──
CARD_EXACTLY_ONE     : '||' ;
CARD_ZERO_ONE_LEFT   : '|o' ;
CARD_ZERO_ONE_RIGHT  : 'o|' ;
CARD_ONE_MANY_LEFT   : '}|' ;
CARD_ONE_MANY_RIGHT  : '|{' ;
CARD_ZERO_MANY_LEFT  : '}o' ;
CARD_ZERO_MANY_RIGHT : 'o{' ;

// ── Connectors ──
IDENT_CONN     : '..' ;
NON_IDENT_CONN : '--' ;

// ── Braces ──
LBRACE : '{' ;
RBRACE : '}' ;

// ── Colon ──
COLON : ':' ;

// ── Identifier ──
ID : [a-zA-Z_\u00C0-\u024F] [a-zA-Z0-9_\u00C0-\u024F]* ;

// ── Catch-all ──
OTHER_INLINE : ~[\r\n \t] ;

