grammar Javamm;

@header {
    package pt.up.fe.comp2026;
}

// -----------------------------------------------------------------------------
// -- LEXER RULES
// -----------------------------------------------------------------------------

// Keywords
BREAK: 'break';
CLASS: 'class';
CONTINUE: 'continue';
DO: 'do';
DOUBLE: 'double';
ELSE: 'else';
ENUM: 'enum';
EXTENDS: 'extends';
FLOAT: 'float';
FOR: 'for';
IF: 'if';
IMPORT: 'import';
INT: 'int';
NEW: 'new';
NULL: 'null';
PACKAGE: 'package';
PRIVATE: 'private';
PROTECTED: 'protected';
PUBLIC: 'public';
RETURN: 'return';
STATIC: 'static';
SWITCH: 'switch';
THIS: 'this';
WHILE: 'while';

// Literals
BOOLEAN: 'false' | 'true';
INTEGER: '0' | [1-9][0-9]*;
VOID: 'void';

// Identifiers
ID: [a-zA-Z$_][a-zA-Z0-9$_]*;

// Whitespace
WS: [ \t\n\r\f]+ -> skip;

// Comments
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
SINGLE_COMMENT: '//' ~[\r\n]* -> skip;

// -----------------------------------------------------------------------------
// -- PARSER RULES
// -----------------------------------------------------------------------------

program: packageDecl importDecl* classNode = classDecl EOF;

packageDecl: PACKAGE path += ID ('.' path += ID)* ';';

importDecl: IMPORT path += ID ('.' path += ID)* ';';

classDecl:
	CLASS name = ID (EXTENDS superclass = ID)? '{' (
		varDecl
		| methodDecl
	)* '}';

varDecl: typeNode = type name = ID ('=' expr)? ';';

methodDecl
	locals[boolean isStatic=false]:
	visibility = (PUBLIC | PROTECTED | PRIVATE)? (
		STATIC {$isStatic=true;}
	)? returnType = type name = ID '(' (param (',' param)*)? ')' '{' varDecl* stmt* '}';

param: typeNode = type name = ID;

type:
	type '[' ']'
	| name = BOOLEAN
	| name = INT
	| name = VOID
	| name = ID;

stmt:
	'{' stmt* '}'						# BlockStmt
	| IF '(' expr ')' stmt (ELSE stmt)*	# IfStmt
	| DO stmt WHILE '(' expr ')' ';'	# DoWhileStmt
	| FOR '(' (var = ID ('[' expr ']')? '=' expr)? ';' expr? ';' (
		var = ID ('[' expr ']')? '=' expr
	)? ')' stmt								# ForStmt
	| WHILE '(' expr ')' stmt				# WhileStmt
	| expr ';'								# ExprStmt
	| var = ID ('[' expr ']')* '=' expr ';'	# AssignStmt
	| ID '[' expr ']' '=' expr ';'			# ArrayStmt
	| RETURN expr? ';'						# ReturnStmt;

args: (expr (',' expr)*);

expr:
	expr '[' expr ']'											# ArrayAcessExpr
	| expr '.' 'length'											# LengthExpr
	| expr '.' name = ID ('(' args? ')')?						# MethodCallExpr
	| expr op = ('*' | '/' | '%') expr							# BinaryExpr
	| expr op = ('+' | '-') expr								# BinaryExpr
	| expr op = ('<' | '>' | '<=' | '>=' | '==' | '!=') expr	# BinaryExpr
	| expr op = ('&&' | '||') expr								# BinaryExpr
	| name = ID '(' args? ')'									# FunctionCall
	| NEW INT '[' ']' '{' args? '}'								# ArrayExpr
	| NEW INT (open += '[' expr ']')+ (open += '[' ']')*		# ArrayExpr
	| NEW name = ID '(' args? ')'								# NewClassExpr
	| '!' expr													# NegExpr
	| '(' expr ')'												# ParentesesExpr
	| value = INTEGER											# IntegerLiteral
	| value = BOOLEAN											# BooleanLiteral
	| name = ID													# VarRefExpr
	| THIS														# ThisExpr
	| op = '-' '-'? expr										# UnaryExpr
	| op = '+' '+'? expr										# UnaryExpr
	;
