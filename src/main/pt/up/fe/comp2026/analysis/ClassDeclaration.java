package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class ClassDeclaration extends AnalysisVisitor{

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.CLASS_DECL, this::visitClassDecl);
    }

    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        var superClass = table.getSuper();
        if (superClass != null && superClass.contains(table.getClassName())) {
            addReport(newError(classDecl, "Class '" + table.getClassName() + "' cannot extend itself."));
        }
        return null;
    }
}
