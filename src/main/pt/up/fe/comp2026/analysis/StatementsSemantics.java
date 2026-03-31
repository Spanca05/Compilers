package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class StatementsSemantics extends AnalysisVisitor {
    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.IF_STMT, this::visitIfStmt);
        addVisit(JmmKind.WHILE_STMT, this::visitWhileStmt);
        addVisit(JmmKind.RETURN_STMT, this::visitReturnStmt);
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
                    null
            ));
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
            null
        ));
        
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
                null
            ));

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
                        null
                ));
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
                    null
            ));

            return null;
        }

        var expr = returnStmt.getChild(0);
        var exprType = TypeUtils.with(table).getExprType(expr);

        if (!returnType.equals(exprType)) {
            var message = String.format("Return type must be '%s' and not '%s'", returnType, exprType);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmt),
                    NodeUtils.getColumn(returnStmt),
                    message,
                    null
            ));

            return null;
        }

        return null;
    }
}
