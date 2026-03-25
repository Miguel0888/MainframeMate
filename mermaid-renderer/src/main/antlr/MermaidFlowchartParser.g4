/**
 * Parser grammar for Mermaid flowchart / graph diagrams.
 *
 * Uses MermaidFlowchartLexer for tokenisation.
 *
 * Key capability: each {@code edgeChain} rule precisely captures
 * source/target node IDs, arrow tokens, and optional labels — with
 * exact token positions for round-trip source editing.
 */
parser grammar MermaidFlowchartParser;

options { tokenVocab = MermaidFlowchartLexer; }

document
    : NL* directive ( NL+ line )* NL* EOF
    ;

directive
    : ( FLOWCHART | GRAPH ) ID?
    ;

line
    : edgeChain
    | SUBGRAPH lineRest?
    | END
    | lineRest
    ;

/** One or more chained edges on a single line: A --> B --> C */
edgeChain
    : nodeRef ( arrow edgeLabel? nodeRef )+
    ;

/** Node reference: bare ID, or ID followed by a shape bracket token. */
nodeRef
    : ID SHAPE?
    ;

arrow
    : ARROW
    ;

edgeLabel
    : PIPE_OPEN LABEL_TEXT? PIPE_CLOSE
    ;

/** Catch-all for non-edge content. */
lineRest
    : ( ID | ARROW | SHAPE | COLON | SEMI | PIPE_OPEN
      | LABEL_TEXT | PIPE_CLOSE | OTHER )+
    ;

