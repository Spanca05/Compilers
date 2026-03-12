grammar Javamm;

@header {
    package pt.up.fe.comp2026;
}

CLASS: 'class';
INT: 'int';
FLOAT: 'float';
DOUBLE: 'double';
STATIC: 'static';
RETURN: 'return';
PACKAGE: 'package';
IMPORT: 'import';
EXTENDS: 'extends';
PUBLIC: 'public';
PROTECTED: 'protected';
PRIVATE: 'private';
SWITCH: 'switch';
IF: 'if';
ELSE: 'else';
DO: 'do';
WHILE: 'while';
FOR: 'for';
BREAK: 'break';
CONTINUE: 'continue';
ENUM: 'enum';
LENGTH: 'length';
NEW: 'new';
NULL: 'null';
THIS: 'this';

INTEGER : '0' | [1-9][0-9]* ;
BOOLEAN: 'false' | 'true';
VOID: 'void';

ID: [a-zA-Z$_][a-zA-Z0-9$_]* ;


WS : [ \t\n\r\f]+ -> skip ;

SINGLE_COMMENT: '//' ~[\r\n]*-> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;

program
    :  packageDecl importDecl* classNode=classDecl EOF
    ;

importDecl
    : IMPORT ID.ID ('.' ID)* ';'
    ;

//package is mandatory
packageDecl
    : PACKAGE path += ID ('.' path +=ID)* ';'
    ;

classDecl
    : CLASS name=ID
        (EXTENDS ID)?
        '{'
        (varDecl | methodDecl)*
        '}'
    ;

varDecl
    : typeNode = type name=ID ('=' expr)?';'
    ;

param
    : typeNode = type name=ID
;

type
    : type '[' ']'
    | name = INT
    | name = BOOLEAN
    | name = VOID
    | name = ID
    ;


methodDecl locals[boolean isStatic=false]
    : visibility=(PUBLIC | PROTECTED | PRIVATE)? (STATIC {$isStatic=true;})?
        returnType = type name=ID
        '('
            (param(',' param)*)?
        ')'
        '{' varDecl* stmt* '}'
    ;

stmt
    : '{' stmt* '}' #BlockStmt
    | IF '(' expr ')' stmt (ELSE stmt)? #IfStmt
    | DO stmt WHILE '(' expr ')'  ';'?  #DoWhileStmt
    | FOR '(' (var = ID ('[' expr ']')? '=' expr )? ';'? expr? ';' (var = ID ('[' expr ']')? '=' expr )? ')' stmt #ForStmt
    | WHILE '(' expr ')' stmt #WhileStmt
    | var = ID ('[' expr ']')? '=' expr ';' #AssignStmt //
    | expr ';' #ExprStmt
    | RETURN expr? ';' #ReturnStmt
    ;

args
    : (expr (',' expr)*)?
    ;

expr
    : expr op= ('*' | '/' | '%' ) expr #BinaryExpr //
    | expr op= ('+' | '-') expr #BinaryExpr //
    | expr op= ('<' | '>' | '<=' | '>=' | '==' | '!=') expr #BinaryExpr
    | expr op= ('&&' | '||') expr #BinaryExpr
    | expr '[' expr? ']' #ArrayAcessExpr
    | expr '.' LENGTH #LengthExpr
    | expr '.' ID '(' args ')' #MethodCallExpr
    | NEW INT '[' expr ']' #NewArrayExpr
    | NEW ID '(' args ')' #NewClassExpr
    | '!' expr #NegExpr
    | '(' expr ')' #ParentesesExpr
    | value=INTEGER #IntegerLiteral
    | value=BOOLEAN #BooleanLiteral
    | ID '(' args ')' #FunctionCall
    | ID ('--' | '++') #UnaryExpr
    | name=ID #VarRefExpr
    | THIS '.' expr #ThisExpr
    | ('--' | '++') ID #UnaryExpr
    | '-' (ID | INTEGER) #UnaryExpr
    | '+' (ID | INTEGER) #UnaryExpr
    ;



