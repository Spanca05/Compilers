package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import java.util.Optional;

public class StatementsSemantics extends AnalysisVisitor {
    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.IF_STMT, this::visitIfStmt);
        addVisit(JmmKind.WHILE_STMT, this::visitWhileStmt);
        addVisit(JmmKind.RETURN_STMT, this::visitReturnStmt);
        addVisit(JmmKind.VAR_DECL, this::visitVarDecl);
        addVisit(JmmKind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(JmmKind.DO_WHILE_STMT, this::visitDoWhileStmt);
        addVisit(JmmKind.FOR_STMT, this::visitForStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElseThrow();

        var returnType = currentMethod.returnType();

        if (returnType != JmmPrimitiveType.VOID && method.getChildren(JmmKind.RETURN_STMT).isEmpty()) {
            var message = "Non-void method must have a return statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(method),
                    NodeUtils.getColumn(method),
                    message,
                    null));
        }

        return null;
    }

    private Void visitIfStmt(JmmNode ifStmt, SymbolTable table) {
        var condition = ifStmt.getChild(0);
        var condType = TypeUtils.with(table).getExprType(condition);
        if (condType == JmmPrimitiveType.BOOLEAN)
            return null;

        var message = "If condition type is not boolean.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(ifStmt),
                NodeUtils.getColumn(ifStmt),
                message,
                null));

        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        var condition = whileStmt.getChild(0);
        var condType = TypeUtils.with(table).getExprType(condition);

        if (condType == JmmPrimitiveType.BOOLEAN)
            return null;

        var message = "While condition must have type boolean.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(condition),
                NodeUtils.getColumn(condition),
                message,
                null));

        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        var returnType = currentMethod.returnType();

        if (returnType == JmmPrimitiveType.VOID) {
            if (!returnStmt.getChildren().isEmpty()) {
                var message = "Void method cannot return a value.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null));
            }

            return null;
        }

        if (returnStmt.getChildren().isEmpty()) {
            var message = "Non-void method must return a value.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmt),
                    NodeUtils.getColumn(returnStmt),
                    message,
                    null));

            return null;
        }

        var expr = returnStmt.getChild(0);
        var exprType = TypeUtils.with(table).getExprType(expr);

        if (!isAssignable(returnType, exprType, table)) {
            var message = String.format("Return type must be '%s' and not '%s'", returnType, exprType);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmt),
                    NodeUtils.getColumn(returnStmt),
                    message,
                    null));

            return null;
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        var declaredType = TypeUtils.with(table).convertType(varDecl.getChild(0));

        if (varDecl.getChildren().size() <= 1)
            return null;

        var assignExpr = varDecl.getChild(1);
        var exprType = TypeUtils.with(table).getExprType(assignExpr);

        if (!isAssignable(declaredType, exprType, table)) {
            var message = String.format(
                    "Variable '%s' is of type '%s', but is initialized with expression of type '%s'.",
                    varDecl.get("name"),
                    declaredType,
                    exprType);

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varDecl),
                    NodeUtils.getColumn(varDecl),
                    message,
                    null));
        }

        return null;
    }

    public Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        var varName = assignStmt.get(JmmAttributes.ASSIGN_STMT.VAR);
        var rhsExpr = assignStmt.getChild(0);

        JmmType lhsType = null;
        var rhsType = TypeUtils.with(table).getExprType(rhsExpr);

        var localVar = currentMethod.getLocalVariable(varName);

        if (localVar.isPresent()) {
            lhsType = localVar.get().type();
        } else {
            var param = currentMethod.getParameter(varName);

            if (param.isPresent()) {
                lhsType = param.get().type();
            } else {
                var field = table.getField(varName);

                if (field.isPresent()) {
                    lhsType = field.get().type();
                }
            }
        }

        if (lhsType == null)
            return null;

        if (!isAssignable(lhsType, rhsType, table)) {
            var message = String.format(
                    "Cannot assign expression of type '%s' to variable '%s' of type '%s'.",
                    rhsType,
                    varName,
                    lhsType);

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt),
                    NodeUtils.getColumn(assignStmt),
                    message,
                    null));
        }

        return null;
    }

    private boolean isAssignable(JmmType lhsType, JmmType rhsType, SymbolTable table) {
        if (lhsType == null || rhsType == null) {
            return false;
        }

        if (lhsType.equals(rhsType)) {
            return true;
        }

        if (!(lhsType instanceof JmmClassType lhsClass) || !(rhsType instanceof JmmClassType rhsClass)) {
            return false;
        }

        return isSubtypeOf(rhsClass, lhsClass, table);
    }

    private boolean isSubtypeOf(JmmClassType childType, JmmClassType parentType, SymbolTable table) {
        var currentTable = resolveClassTable(childType, table);

        while (currentTable.isPresent()) {
            var current = currentTable.get();
            var currentFqName = current.getFullyQualifiedName();

            if (matchesClassName(currentFqName, parentType) || matchesClassName(current.getClassName(), parentType)) {
                return true;
            }

            var superName = current.getSuper();
            if (superName == null) {
                return false;
            }

            currentTable = resolveClassTable(superName, table);
        }

        return false;
    }

    private Optional<SymbolTable> resolveClassTable(JmmClassType classType, SymbolTable table) {
        return resolveClassTable(classType.fullyQualifiedName(), table);
    }

    private Optional<SymbolTable> resolveClassTable(String className, SymbolTable table) {
        if (table.getClassName().equals(className) || table.getFullyQualifiedName().equals(className)) {
            return Optional.of(table);
        }

        if (!(table instanceof JmmSymbolTable jmmTable)) {
            return Optional.empty();
        }

        var imported = jmmTable.getImportedSymbolTable(className);
        if (imported.isPresent()) {
            return imported;
        }

        var simpleName = className.substring(className.lastIndexOf('.') + 1);
        return jmmTable.getImplicitImport(simpleName);
    }

    private boolean matchesClassName(String candidateName, JmmClassType expectedType) {
        var expectedFqName = expectedType.fullyQualifiedName();
        var expectedSimpleName = expectedFqName.substring(expectedFqName.lastIndexOf('.') + 1);
        var candidateSimpleName = candidateName.substring(candidateName.lastIndexOf('.') + 1);

        return candidateName.equals(expectedFqName)
                || candidateName.equals(expectedSimpleName)
                || candidateSimpleName.equals(expectedSimpleName);
    }

    private Void visitDoWhileStmt(JmmNode doWhileStmt, SymbolTable table) {
        var condition = doWhileStmt.getChild(1);
        var condType = TypeUtils.with(table).getExprType(condition);

        if (condType == JmmPrimitiveType.BOOLEAN)
            return null;

        var message = "Do-While condition must have type boolean.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(condition),
                NodeUtils.getColumn(condition),
                message,
                null));

        return null;
    }

    private Void visitForStmt(JmmNode forStmt, SymbolTable table) {
        var first = forStmt.getChild(0);
        var condition = forStmt.getChild(1);
        var second = forStmt.getChild(2);


        var firstType = TypeUtils.with(table).getExprType(first);
        var condType = TypeUtils.with(table).getExprType(condition);
        var secondType = TypeUtils.with(table).getExprType(second);

        if (firstType != JmmPrimitiveType.INT) {
            var message = "For first expression must have type int. \n";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(condition),
                    NodeUtils.getColumn(condition),
                    message,
                    null));

            return null;
        }

        if (condType != JmmPrimitiveType.BOOLEAN) {
            var message = "For condition must have type boolean. \n";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(condition),
                    NodeUtils.getColumn(condition),
                    message,
                    null));

            return null;
        }

        if (secondType != JmmPrimitiveType.INT) {
            var message = "For second expression must have type int.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(condition),
                    NodeUtils.getColumn(condition),
                    message,
                    null));

            return null;
        }

        return null;
    }
}
