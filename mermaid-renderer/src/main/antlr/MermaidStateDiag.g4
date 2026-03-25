/**
 * Combined ANTLR grammar for Mermaid state diagrams (stateDiagram-v2).
 *
 * Key syntax:
 *   stateDiagram-v2
 *       [*] --> Active
 *       Active --> Inactive : deactivate
 *       state Active {
 *           [*] --> Running
 *       }
 */
grammar MermaidStateDiag;

// ═══════════════════════════════════════════════════════════════
//  Parser rules
// ═══════════════════════════════════════════════════════════════

document
    : NL* directive ( NL+ line )* NL* EOF
    ;

directive
    : STATE_DIAG
    ;

line
    : transition
    | compositeStart
    | RBRACE
    | noteLine
    | lineRest
    ;

/** State transition:  stateRef --> stateRef (: label)? */
transition
    : stateRef ARROW stateRef ( COLON guard )?
    ;

stateRef
    : STATE_START_END        // [*]
    | ID
    ;

guard
    : ( ID | OTHER_INLINE )+
    ;

/** Composite state block opening:  state Name { */
compositeStart
    : STATE ID LBRACE
    ;

noteLine
    : NOTE notePosition? stateRef COLON lineRest?
    ;

notePosition
    : ID ID  // "right of" or "left of" — parsed as two IDs
    ;

lineRest
    : ( ID | ARROW | COLON | STATE_START_END | LBRACE | RBRACE | STATE
      | NOTE | OTHER_INLINE )+
    ;

// ═══════════════════════════════════════════════════════════════
//  Lexer rules
// ═══════════════════════════════════════════════════════════════

LINE_COMMENT : '%%' ~[\r\n]* -> skip ;
WS           : [ \t]+ -> channel(HIDDEN) ;
NL           : '\r'? '\n' ;

// ── Keywords ──
STATE_DIAG      : 'stateDiagram-v2' | 'stateDiagram' ;
STATE           : 'state' ;
NOTE            : 'note' ;

// ── State start/end marker ──
STATE_START_END : '[*]' ;

// ── Arrow (only --> in state diagrams) ──
ARROW : '-->' ;

// ── Braces for composite states ──
LBRACE : '{' ;
RBRACE : '}' ;

// ── Colon (separates label/guard) ──
COLON : ':' ;

// ── Identifier ──
ID : [a-zA-Z_\u00C0-\u024F] [a-zA-Z0-9_\u00C0-\u024F]* ;

// ── Catch-all for inline content ──
OTHER_INLINE : ~[\r\n \t] ;

