/**
 * ANTLR4 Parser Grammar for IBM z/OS JCL (Job Control Language)
 * Designed for outline extraction.
 *
 * Compatible with ANTLR 4.9.3 / Java 8.
 */
parser grammar JCLParser;

options { tokenVocab = JCLLexer; }

// ── top-level ───────────────────────────────────────────────────────
jclDeck
    : ( statement | NL )* EOF
    ;

statement
    : jobStmt
    | execStmt
    | ddStmt
    | procStmt
    | pendStmt
    | setStmt
    | includeStmt
    | jcllibStmt
    | ifStmt
    | elseStmt
    | endifStmt
    | outputStmt
    | nullStmt
    ;

// ── JOB ─────────────────────────────────────────────────────────────
jobStmt
    : SLASH_SLASH name JOB paramList? NL
    ;

// ── EXEC ────────────────────────────────────────────────────────────
execStmt
    : SLASH_SLASH name? EXEC paramList NL
    ;

// ── DD ──────────────────────────────────────────────────────────────
ddStmt
    : SLASH_SLASH name? DD paramList? NL
    ;

// ── PROC / PEND ─────────────────────────────────────────────────────
procStmt
    : SLASH_SLASH name? PROC paramList? NL
    ;

pendStmt
    : SLASH_SLASH PEND NL
    ;

// ── SET ─────────────────────────────────────────────────────────────
setStmt
    : SLASH_SLASH SET paramList NL
    ;

// ── INCLUDE ─────────────────────────────────────────────────────────
includeStmt
    : SLASH_SLASH INCLUDE paramList NL
    ;

// ── JCLLIB ──────────────────────────────────────────────────────────
jcllibStmt
    : SLASH_SLASH JCLLIB paramList NL
    ;

// ── IF / ELSE / ENDIF ───────────────────────────────────────────────
ifStmt
    : SLASH_SLASH IF anyTokens THEN NL
    ;

elseStmt
    : SLASH_SLASH ELSE NL
    ;

endifStmt
    : SLASH_SLASH ENDIF NL
    ;

// ── OUTPUT ──────────────────────────────────────────────────────────
outputStmt
    : SLASH_SLASH name? OUTPUT paramList? NL
    ;

// ── null statement (just //) ────────────────────────────────────────
nullStmt
    : SLASH_SLASH NL
    ;

// ── parameters ──────────────────────────────────────────────────────
paramList
    : param ( COMMA param? )*
    ;

param
    : name EQ paramValue    // keyword param
    | paramValue            // positional param
    ;

paramValue
    : LPAREN paramValueList RPAREN
    | STRING
    | name
    | NUMBER
    | STAR
    | AMP name              // symbolic: &VAR
    | name DOT name         // qualified: A.B
    ;

paramValueList
    : paramValue ( COMMA paramValue )*
    ;

// ── helpers ─────────────────────────────────────────────────────────
name
    : NAME
    | JOB | EXEC | DD | PROC | PEND | SET | INCLUDE | JCLLIB
    | OUTPUT | IF | THEN | ELSE | ENDIF       // keywords can also be names
    ;

// Consume arbitrary tokens until THEN (used in IF conditions)
anyTokens
    : anyToken+
    ;

anyToken
    : NAME | NUMBER | STRING | EQ | COMMA | LPAREN | RPAREN
    | DOT | STAR | AMP | JOB | EXEC | DD | PROC | PEND
    | SET | INCLUDE | JCLLIB | OUTPUT | IF | ELSE | ENDIF
    ;

