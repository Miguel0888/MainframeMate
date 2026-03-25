/**
 * Lexer grammar for Mermaid flowchart / graph diagrams.
 *
 * Uses LABEL_MODE for pipe-delimited edge labels |text|
 * so that whitespace inside labels is preserved.
 */
lexer grammar MermaidFlowchartLexer;

// ── Comments ──
LINE_COMMENT : '%%' ~[\r\n]* -> skip ;

// ── Whitespace (preserved for round-trip editing) ──
WS : [ \t]+ -> channel(HIDDEN) ;

// ── Newlines — significant: they separate statements ──
NL : '\r'? '\n' ;

// ── Keywords (must precede ID rule) ──
FLOWCHART : 'flowchart' ;
GRAPH     : 'graph' ;
SUBGRAPH  : 'subgraph' ;
END       : 'end' ;

// ── Arrow operators (longest-match-first) ──
ARROW
    : '<-->'                               // bidirectional
    | '<==>'
    | '<-.->'
    | '==>'  | '==x' | '==o' | '==='      // thick
    | '-.->' | '-.-x' | '-.-o' | '-.-'     // dashed / dotted
    | '-->'  | '--x'  | '--o'  | '---'     // solid
    ;

// ── Shape content — greedily captures bracket-delimited labels ──
// Order: multi-char openers before single-char openers.
SHAPE
    : '([' ( ~[\]\r\n] )* '])'            // stadium
    | '(((' ( ~[)\r\n] )*? ')))'          // double circle
    | '((' ( ~[)\r\n] )*? '))'            // circle
    | '{{' ( ~[}\r\n] )*? '}}'            // hexagon
    | '[[' ( ~[\]\r\n] )*? ']]'           // subroutine
    | '[(' ( ~[\]\r\n] )*? ')]'           // cylinder
    | '[/' ( ~[\]\r\n] )*? '/]'           // trapezoid
    | '[\\' ( ~[\]\r\n] )*? '\\]'        // inv. trapezoid
    | '[/' ( ~[\]\r\n] )*? '\\]'         // parallelogram (lean right)
    | '[\\' ( ~[\]\r\n] )*? '/]'         // parallelogram (lean left)
    | '[' ( ~[\]\r\n] )* ']'              // rectangle (default)
    | '(' ( ~[)\r\n] )*? ')'              // round rect
    | '{' ( ~[}\r\n] )*? '}'              // diamond
    | '>' ( ~[\]\r\n] )* ']'              // asymmetric / flag
    ;

// ── Pipe — opens LABEL_MODE for edge labels ──
PIPE_OPEN : '|' -> pushMode(LABEL_MODE) ;

// ── Punctuation ──
COLON : ':' ;
SEMI  : ';' ;

// ── Identifier ──
ID : [a-zA-Z_\u00C0-\u024F] [a-zA-Z0-9_\u00C0-\u024F]* ;

// ── Catch-all ──
OTHER : ~[\r\n \t] ;

// ═══════════════════════════════════════════════════════════════
//  Lexer mode: inside pipe-delimited edge label  |...|
// ═══════════════════════════════════════════════════════════════

mode LABEL_MODE ;

LABEL_TEXT  : ~[|\r\n]+ ;
PIPE_CLOSE  : '|' -> popMode ;
LABEL_NL    : '\r'? '\n' -> type(NL), popMode ;

