package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class EntityOpsSemantics extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(JmmKind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(JmmKind.NEG_EXPR, this::visitNegExpr);
    }

    public Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        var op = binaryExpr.get("op");
        var leftType = TypeUtils.with(table).getExprType(binaryExpr.getChild(0));
        var rightType = TypeUtils.with(table).getExprType(binaryExpr.getChild(1));

        if (leftType == null || rightType == null) return null;

        var exprType = TypeUtils.with(table).getExprType(binaryExpr);
        if (exprType != null) return null;

        var message = String.format(
                "Operator '%s' cannot be applied to operands of type '%s' and '%s'.",
                op,
                leftType,
                rightType
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(binaryExpr),
                NodeUtils.getColumn(binaryExpr),
                message,
                null
        ));

        return null;
    }

    public Void visitNegExpr(JmmNode negExpr, SymbolTable table) {
        var exprType = TypeUtils.with(table).getExprType(negExpr.getChild(0));

        if (exprType == null) return null;

        if (exprType.equals(JmmPrimitiveType.BOOLEAN)) return null;

        var message = String.format(
                "Operator ! cannot be applied to operand of type '%s'.",
                exprType
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(negExpr),
                NodeUtils.getColumn(negExpr),
                message,
                null
        ));

        return null;
    }
}
