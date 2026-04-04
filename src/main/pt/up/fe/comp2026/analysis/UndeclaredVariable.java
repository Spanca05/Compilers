package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class UndeclaredVariable extends AnalysisVisitor {
    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        //System.out.println(signature);

        currentMethod = table.getMethod(signature).orElseThrow();
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        var varRefName = varRefExpr.get("name");

        if (currentMethod.getParameter(varRefName).isPresent())
            return null;

        if (currentMethod.getLocalVariable(varRefName).isPresent())
            return null;

        if(table.getField(varRefName).isPresent())
            return null;

        if (varRefName.equals(table.getClassName())) return null;
        if (table.getImportedFullyQualifiedName(varRefName).isPresent()) return null;

        var message = String.format("Variable '%s' does not exist.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn((varRefExpr)),
                message,
                null
        ));

        return null;
    }
}
