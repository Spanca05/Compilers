package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

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

public class ArraySemantics extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.ARRAY_ACESS_EXPR, this::visitArrayAccessExpr);
        addVisit(JmmKind.ARRAY_EXPR, this::visitArrayExpr);
        addVisit(JmmKind.LENGTH_EXPR, this::visitLengthExpr);
        addVisit(JmmKind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElseThrow();
        return null;
    }

    private Void visitArrayAccessExpr(JmmNode arrayAccess, SymbolTable table) {
        var targetType = TypeUtils.with(table).getExprType(arrayAccess.getChild(0));
        var indexType  = TypeUtils.with(table).getExprType(arrayAccess.getChild(1));

        if (targetType != null && !(targetType instanceof JmmArrayType)) {
            addReport(newError(arrayAccess,
                    "Array access requires an array type, but got '" + targetType + "'."));
        }

        if (indexType != null && !indexType.equals(JmmPrimitiveType.INT)) {
            addReport(newError(arrayAccess,
                    "Array index must be of type int, but got '" + indexType + "'."));
        }

        return null;
    }

    private Void visitArrayExpr(JmmNode arrayExpr, SymbolTable table) {
        var argsChildren = arrayExpr.getChildren(JmmKind.ARGS);

        if (argsChildren.isEmpty()) {
            for (var child : arrayExpr.getChildren()) {
                var sizeType = TypeUtils.with(table).getExprType(child);
                if (sizeType != null && !sizeType.equals(JmmPrimitiveType.INT)) {
                    addReport(newError(arrayExpr,
                            "Array size must be of type int, but got '" + sizeType + "'."));
                }
            }
        } else {
            var args = argsChildren.getFirst();
            for (var element : args.getChildren()) {
                var elemType = TypeUtils.with(table).getExprType(element);
                if (elemType != null && !elemType.equals(JmmPrimitiveType.INT)) {
                    addReport(newError(arrayExpr,
                            "Array initializer element must be of type int, but got '" + elemType + "'."));
                }
            }
        }

        return null;
    }

    private Void visitLengthExpr(JmmNode lengthExpr, SymbolTable table) {
        var targetType = TypeUtils.with(table).getExprType(lengthExpr.getChild(0));

        if (targetType != null && !(targetType instanceof JmmArrayType)) {
            addReport(newError(lengthExpr,
                    "'.length' can only be used on arrays, but got '" + targetType + "'."));
        }

        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        var op = binaryExpr.get("op");

        if (!op.equals("+") && !op.equals("-") && !op.equals("*") && !op.equals("/") && !op.equals("%")) {
            return null;
        }

        var leftType  = TypeUtils.with(table).getExprType(binaryExpr.getChild(0));
        var rightType = TypeUtils.with(table).getExprType(binaryExpr.getChild(1));

        if (leftType instanceof JmmArrayType) {
            addReport(newError(binaryExpr,
                    "Arrays cannot be used in arithmetic expressions."));
        }

        if (rightType instanceof JmmArrayType) {
            addReport(newError(binaryExpr,
                    "Arrays cannot be used in arithmetic expressions."));
        }

        return null;
    }
}