package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class ImportValidation extends AnalysisVisitor{

    @Override
    public void buildVisitor(){
        //addVisit(JmmKind.NEW_CLASS_EXPR, this::visitNewClassExpr);
        addVisit(JmmKind.VAR_DECL, this::visitVarDecl);
    }

    public Void visitNewClassExpr(JmmNode newClassExpr, SymbolTable table){

        return null;
    }

    public Void visitVarDecl(JmmNode varDecl, SymbolTable table){
        var typeNode = varDecl.getObject(JmmAttributes.VAR_DECL.TYPE_NODE, JmmNode.class);
        var typeName = typeNode.get("name");

        // Skip primitives
        if (JmmPrimitiveType.fromString(typeName).isPresent()) return null;

        // Check it's imported or is the class itself
        if (typeName.equals(table.getClassName())) return null;
        if (table.getImportedFullyQualifiedName(typeName).isPresent()) return null;

        addReport(newError(varDecl, "Class '" + typeName + "' is not imported."));
        return null;
    }

}
