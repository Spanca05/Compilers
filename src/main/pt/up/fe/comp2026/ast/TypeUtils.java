package pt.up.fe.comp2026.ast;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;


/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static TypeUtils with(SymbolTable table) {
        return new TypeUtils(table);
    }

    public static JmmPrimitiveType intType() {
        return JmmPrimitiveType.INT;
    }

    public JmmType convertType(JmmNode typeNode) {
        assert (TYPE.check(typeNode));

        //System.out.println("[TODO] TypeUtils.convertType(): Implement for classes and arrays");
        var name = typeNode.get("name");

        if (typeNode.getAttributes().contains("name")) {
            var typeName = typeNode.get("name");
            var primitive = JmmPrimitiveType.fromString(typeName);

            if (primitive.isPresent())
                return primitive.get();

            return new JmmClassType(typeName, false, false);
        }

        // Array case
        var innerTypeNode = typeNode.getChildren(TYPE).getFirst();
        var innerType = convertType(innerTypeNode); // recursive, calls itself instead of buildType

        if (innerType instanceof JmmArrayType arrayType) {
            return new JmmArrayType(arrayType.itemType(), arrayType.dimension() + 1);
        }

        return JmmPrimitiveType.fromString(name).orElseThrow();
    }


    /**
     * Gets the {@link JmmType} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public JmmType getExprType(JmmNode expr) {
        return switch (expr.getKind()) {
            case INTEGER_LITERAL -> intType();
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr);
            default ->
                    throw new UnsupportedOperationException("Can't compute type for expression kind '" + expr.getKind() + "'");
        };
    }


    public Signature getMethodDeclSignature(JmmNode methodDecl) {
        // Ensure given node is a MethodDecl
        METHOD_DECL.check(methodDecl);

        // Get name of the method
        var methodName = methodDecl.get("name");

        //System.out.println("[TODO] TypeUtils.getMethodDeclSignature(): Supporting only methods with a single parameter that is an int, needs to be expanded");
        var params = methodDecl.getChildren(PARAM).stream()
                .map(param -> convertType(param.getObject(JmmAttributes.PARAM.TYPE_NODE, JmmNode.class))).toList();

        // Create method signature with method name and types of parameters
        return new Signature(methodName, params);
    }


    private JmmType getBinExprType(JmmNode binaryExpr) {

        // Get operator
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*" -> intType();
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private JmmType getVarExprType(JmmNode varRefExpr) {
        //System.out.println("[TODO] TypeUtils.getVarExprType(): Implement type inference for VarExpr. You will need to determine in which method the VarRef is and use the symbol table");
        var varName = varRefExpr.get("name");

        var methodNode = varRefExpr.getAncestor(METHOD_DECL).orElseThrow();
        var signature = getMethodDeclSignature(methodNode);
        var method = table.getMethod(signature).orElseThrow();

        var local = method.getLocalVariable(varName);
        if(local.isPresent()) return local.get().type();

        var param = method.getParameter(varName);
        if(param.isPresent()) return param.get().type();

        // Undeclared — UndeclaredVariable pass handles the error
        var field = table.getFields().stream()
                .filter(f -> f.name().equals(varName))
                .findFirst();
        return field.map(Symbol::type).orElse(null);

    }

}
