/**
 * Combined ANTLR grammar for Mermaid sequence diagrams.
 *
 * Key syntax:
 *   sequenceDiagram
 *       participant Alice
 *       Alice->>Bob: Hello
 *       loop Discussion
 *           Bob-->>Alice: Reply
 *       end
 */
grammar MermaidSequence;

// ═══════════════════════════════════════════════════════════════
//  Parser rules
// ═══════════════════════════════════════════════════════════════

document
    : NL* directive ( NL+ line )* NL* EOF
    ;

directive
    : SEQ_DIAG
    ;

line
    : message
    | participantDecl
    | fragmentStart
    | fragmentElse
    | END
    | noteLine
    | lineRest
    ;

/** Sequence message: Actor ARROW Actor : text */
message
    : ID seqArrow ID ( COLON messageText )?
    ;

seqArrow
    : SEQ_ARROW
    ;

messageText
    : ( ID | OTHER_INLINE )+
    ;

participantDecl
    : ( PARTICIPANT | ACTOR ) ID ( AS ID )?
    ;

fragmentStart
    : ( LOOP | ALT | OPT | PAR | CRITICAL | BREAK | RECT ) fragmentCondition?
    ;

fragmentElse
    : ELSE fragmentCondition?
    ;

fragmentCondition
    : ( ID | OTHER_INLINE )+
    ;

noteLine
    : NOTE notePosition? ( ID | OTHER_INLINE )+ COLON? ( ID | OTHER_INLINE )*
    ;

notePosition
    : ID ID  // "right of", "left of"
    ;

lineRest
    : ( ID | SEQ_ARROW | COLON | OTHER_INLINE )+
    ;

// ═══════════════════════════════════════════════════════════════
//  Lexer rules
// ═══════════════════════════════════════════════════════════════

LINE_COMMENT : '%%' ~[\r\n]* -> skip ;
WS           : [ \t]+ -> channel(HIDDEN) ;
NL           : '\r'? '\n' ;

// ── Keywords ──
SEQ_DIAG    : 'sequenceDiagram' ;
PARTICIPANT : 'participant' ;
ACTOR       : 'actor' ;
AS          : 'as' ;
LOOP        : 'loop' ;
ALT         : 'alt' ;
OPT         : 'opt' ;
PAR         : 'par' ;
CRITICAL    : 'critical' ;
BREAK       : 'break' ;
RECT        : 'rect' ;
ELSE        : 'else' ;
END         : 'end' ;
NOTE        : 'note' ;

// ── Sequence arrows (longest first!) ──
SEQ_ARROW
    : '-->>'      // dashed + filled arrowhead
    | '->>+'      // solid + filled + activate
    | '->>-'      // solid + filled + deactivate
    | '->>'       // solid + filled arrowhead
    | '-->'       // dashed + open arrowhead
    | '->'        // solid + open arrowhead
    | '--x'       // dashed + cross
    | '-x'        // solid + cross
    | '--)'       // dashed + async
    | '-)'        // solid + async
    ;

// ── Colon ──
COLON : ':' ;

// ── Identifier ──
ID : [a-zA-Z_\u00C0-\u024F] [a-zA-Z0-9_\u00C0-\u024F]* ;

// ── Catch-all ──
OTHER_INLINE : ~[\r\n \t] ;

