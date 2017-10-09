grammar Graql;

queryList : query* EOF ;

queryEOF       : query EOF ;
query          : getQuery | insertQuery | defineQuery | undefineQuery | deleteQuery | aggregateQuery | computeQuery ;

matchPart      : MATCH patterns                             # matchBase
               | matchPart 'limit' INTEGER              ';' # matchLimit
               | matchPart 'offset' INTEGER             ';' # matchOffset
               | matchPart 'order' 'by' VARIABLE ORDER? ';' # matchOrderBy
               ;

getQuery       : matchPart 'get' (VARIABLE (',' VARIABLE)*)? ';' ;
insertQuery    : matchPart? INSERT varPatterns ;
defineQuery    : DEFINE varPatterns ;
undefineQuery  : UNDEFINE varPatterns ;
deleteQuery    : matchPart 'delete' variables? ';' ;
aggregateQuery : matchPart 'aggregate' aggregate ';' ;
computeQuery   : 'compute' computeMethod ;

variables      : VARIABLE (',' VARIABLE)* ;

computeMethod  : min | max | median | mean | std | sum | count | path | cluster | degrees ;

min            : MIN      'of' ofList      ('in' inList)? ';' ;
max            : MAX      'of' ofList      ('in' inList)? ';' ;
median         : MEDIAN   'of' ofList      ('in' inList)? ';' ;
mean           : MEAN     'of' ofList      ('in' inList)? ';' ;
std            : STD      'of' ofList      ('in' inList)? ';' ;
sum            : SUM      'of' ofList      ('in' inList)? ';' ;
degrees        : DEGREES ('of' ofList)?    ('in' inList)? ';' ;
cluster        : CLUSTER                   ('in' inList)? ';' clusterParam* ;
path           : PATH    'from' id 'to' id ('in' inList)? ';' ;
count          : COUNT                     ('in' inList)? ';' ;

clusterParam   : MEMBERS      ';' # clusterMembers
               | SIZE INTEGER ';' # clusterSize
               ;

ofList         : labelList ;
inList         : labelList ;
labelList      : label (',' label)* ;

aggregate      : identifier argument*             # customAgg
               | '(' namedAgg (',' namedAgg)* ')' # selectAgg
               ;
argument       : VARIABLE  # variableArgument
               | aggregate # aggregateArgument
               ;
namedAgg       : aggregate 'as' identifier ;

patterns       : (pattern ';')+ ;
pattern        : varPattern                    # varPatternCase
               | pattern 'or' pattern          # orPattern
               | '{' patterns '}'              # andPattern
               ;

varPatterns    : (varPattern ';')+ ;
varPattern     : VARIABLE | variable? property (','? property)* ;

property       : 'isa' variable                     # isa
               | 'sub' variable                     # sub
               | 'relates' variable                 # relates
               | 'plays' variable                   # plays
               | 'id' id                            # propId
               | 'label' label                      # propLabel
               | 'val' predicate                    # propValue
               | 'when' '{' patterns '}'            # propWhen
               | 'then' '{' varPatterns '}'         # propThen
               | 'has' label (resource=VARIABLE | predicate) ('via' relation=VARIABLE)?# propHas
               | 'has' variable                     # propResource
               | 'key' variable                     # propKey
               | '(' casting (',' casting)* ')'     # propRel
               | 'is-abstract'                      # isAbstract
               | 'datatype' DATATYPE                # propDatatype
               | 'regex' REGEX                      # propRegex
               | '!=' variable                      # propNeq
               ;

casting        : variable (':' VARIABLE)?
               | variable VARIABLE         {notifyErrorListeners("expecting {',', ':'}");};

variable       : label | VARIABLE ;

predicate      : '='? value                     # predicateEq
               | '=' VARIABLE                   # predicateVariable
               | '!=' valueOrVar                # predicateNeq
               | '>' valueOrVar                 # predicateGt
               | '>=' valueOrVar                # predicateGte
               | '<' valueOrVar                 # predicateLt
               | '<=' valueOrVar                # predicateLte
               | 'contains' (STRING | VARIABLE) # predicateContains
               | REGEX                          # predicateRegex
               ;
valueOrVar     : VARIABLE # valueVariable
               | value    # valuePrimitive
               ;
value          : STRING   # valueString
               | INTEGER  # valueInteger
               | REAL     # valueReal
               | BOOLEAN  # valueBoolean
               | DATE     # valueDate
               | DATETIME # valueDateTime
               ;

label          : identifier | IMPLICIT_IDENTIFIER;
id             : identifier ;

// Some keywords can also be used as identifiers
identifier     : ID | STRING
               | MIN | MAX| MEDIAN | MEAN | STD | SUM | COUNT | PATH | CLUSTER
               | DEGREES | MEMBERS | SIZE
               ;

// keywords
MIN            : 'min' ;
MAX            : 'max' ;
MEDIAN         : 'median' ;
MEAN           : 'mean' ;
STD            : 'std' ;
SUM            : 'sum' ;
COUNT          : 'count' ;
PATH           : 'path' ;
CLUSTER        : 'cluster' ;
DEGREES        : 'degrees' ;
MEMBERS        : 'members' ;
SIZE           : 'size' ;
MATCH          : 'match' ;
INSERT         : 'insert' ;
DEFINE         : 'define' ;
UNDEFINE       : 'undefine' ;

DATATYPE       : 'long' | 'double' | 'string' | 'boolean' | 'date' ;
ORDER          : 'asc' | 'desc' ;
BOOLEAN        : 'true' | 'false' ;
VARIABLE       : '$' [a-zA-Z0-9_-]+ ;
ID             : [a-zA-Z_] [a-zA-Z0-9_-]* ;
STRING         : '"' (~["\\] | ESCAPE_SEQ)* '"' | '\'' (~['\\] | ESCAPE_SEQ)* '\'';
REGEX          : '/' (~'/' | '\\/')* '/' ;
INTEGER        : ('+' | '-')? [0-9]+ ;
REAL           : ('+' | '-')? [0-9]+ '.' [0-9]+ ;
DATE           : DATE_FRAGMENT ;
DATETIME       : DATE_FRAGMENT 'T' TIME ;

fragment DATE_FRAGMENT : YEAR '-' MONTH '-' DAY ;
fragment MONTH         : [0-1][0-9] ;
fragment DAY           : [0-3][0-9] ;
fragment YEAR          : [0-9][0-9][0-9][0-9] | ('+' | '-') [0-9]+ ;
fragment TIME          : HOUR ':' MINUTE (':' SECOND)? ;
fragment HOUR          : [0-2][0-9] ;
fragment MINUTE        : [0-6][0-9] ;
fragment SECOND        : [0-6][0-9] ('.' [0-9]+)? ;

fragment ESCAPE_SEQ : '\\' . ;

COMMENT : '#' .*? '\r'? ('\n' | EOF) -> channel(HIDDEN) ;

IMPLICIT_IDENTIFIER : '@' [a-zA-Z0-9_-]+ ;

WS : [ \t\r\n]+ -> channel(HIDDEN) ;

// Unused lexer rule to help with autocomplete on variable names
DOLLAR : '$' ;
