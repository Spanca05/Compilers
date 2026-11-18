# Java-- Compiler

A compiler for Java--, a subset of Java, that generates JVM bytecode. Developed for the Compilers course at FEUP (University of Porto), 2025/2026.

## Pipeline
1. **Parsing:** ANTLR grammar producing an abstract syntax tree
2. **Symbol table:** imports, class fields, methods, parameters and locals
3. **Semantic analysis:** type checking of expressions, method calls, arrays and control-flow statements
4. **Intermediate code (OLLIR):** generation for expressions, conditionals, `while`/`for`/`do-while` loops, arrays and object instantiation, with short-circuit `&&`/`||`
5. **Optimisations:** constant folding and propagation, dead-code and branch elimination, register allocation
6. **Code generation:** Jasmin assembly for the JVM (fields, method invocation, object creation)

## Tech stack
Java · ANTLR · Gradle · JUnit

## Build and run
```bash
./gradlew installDist        # build
./jmm -i=path/to/file.jmm    # parse a file and print its AST
./gradlew test               # run the test suite (all compiler stages)
```
The command-line launcher currently stops after parsing; the later stages (semantic analysis, OLLIR, optimisations and Jasmin) are exercised through the test suite.

## AI use
Claude and ChatGPT were used for research and guidance; all code was reviewed and validated by the team.

## Authors
Vasco Vieira · Cláudio Meireles · Leonardo Carvalho · Miguel Pereira
