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

    public static JmmPrimitiveType booleanType() {
        return JmmPrimitiveType.BOOLEAN;
    }

    public JmmType convertType(JmmNode typeNode) {
        assert (TYPE.check(typeNode));

        //System.out.println("[TODO] TypeUtils.convertType(): Implement for classes and arrays");

        if (typeNode.getAttributes().contains("name")) {
            var typeName = typeNode.get("name");
            var primitive = JmmPrimitiveType.fromString(typeName);

            if (primitive.isPresent())
                return primitive.get();

            if (typeName.equals(table.getClassName())) {
                return new JmmClassType(table.getFullyQualifiedName(), false, false);
            }

            var fullyQualified = table.getImportedFullyQualifiedName(typeName);
            var resolvedName = fullyQualified.orElse(typeName);
            var isImported = fullyQualified.isPresent();
            return new JmmClassType(resolvedName, isImported, false);
        }

        // Array case
        var innerTypeNode = typeNode.getChildren(TYPE).getFirst();
        var innerType = convertType(innerTypeNode); // recursive, calls itself instead of buildType

        if (innerType instanceof JmmArrayType arrayType) {
            return new JmmArrayType(arrayType.itemType(), arrayType.dimension() + 1);
        }

        return new JmmArrayType(innerType, 1);
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
            case BOOLEAN_LITERAL -> booleanType();
            case ARRAY_EXPR -> getArrayExprType(expr);
            case NEW_CLASS_EXPR -> getNewClassExprType(expr);
            case METHOD_CALL_EXPR -> getMethodCallExprType(expr);
            case NEG_EXPR -> getNegExprType(expr);
            case PARENTESES_EXPR -> getParentesesExprType(expr);
            case THIS_EXPR -> getThisExprType(expr);
            case ARRAY_ACESS_EXPR -> getArrayAccessExprType(expr);
            case LENGTH_EXPR -> getLengthExprType(expr);
            case UNARY_EXPR -> getUnaryExprType(expr);
            case FUNCTION_CALL -> getFunctionCallExprType(expr);
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
        var left = binaryExpr.getChild(0);
        var right = binaryExpr.getChild(1);
        var leftType = getExprType(left);
        var rightType = getExprType(right);

        if (leftType == null || rightType == null) return null;

        // Get operator
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/", "%" ->
                    leftType.equals(intType()) && rightType.equals(intType())
                    ? intType()
                    : null;

            case "<", ">", "<=", ">=" ->
                    leftType.equals(intType()) && rightType.equals(intType())
                            ? booleanType()
                            : null;

            case "==", "!=" ->
                leftType.equals(rightType)
                        ? booleanType()
                        : null;

            case "&&", "||" ->
                    leftType.equals(booleanType()) && rightType.equals(booleanType())
                            ? booleanType()
                            : null;

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

    private JmmType getNewClassExprType(JmmNode newClassExpr) {
        var className = newClassExpr.get("name");

        // Classe atual
        if (className.equals(table.getClassName())) {
            return new JmmClassType(table.getFullyQualifiedName(), false, false);
        }

        // Import explícito
        var importFq = table.getImportedFullyQualifiedName(className);

        if (importFq.isPresent()) {
            return new JmmClassType(importFq.get(), true, false);
        }

        // Import implícito
        if (table instanceof JmmSymbolTable jmmTable && jmmTable.isImplicitImport(className)) {
            return new JmmClassType(className, false, false);
        }

        // Fallback
        return new JmmClassType(className, false, false);
    }

    private JmmType getMethodCallExprType(JmmNode methodCallExpr) {
        var methodName = methodCallExpr.get(JmmAttributes.METHOD_CALL_EXPR.NAME);

        var argTypes = getArgTypes(methodCallExpr);

        var signature = new Signature(methodName, argTypes);

        var receiver = methodCallExpr.getChild(0);

        if (JmmKind.THIS_EXPR.check(receiver)) {
            var maybeMethod = getMethodFromCurrentOrSuper(signature);

            if (maybeMethod.isPresent()) return maybeMethod.get().returnType();

            return null;
        }

        var receiverType = getExprType(receiver);

        var targetTable = resolveTableFromReceiverType(receiverType);

        if (targetTable.isEmpty()) return null;

        var maybeMethod = targetTable.get().getMethod(signature);

        if (maybeMethod.isPresent()) return normalizeImplicitClassType(maybeMethod.get().returnType());

        return null;
    }

    private JmmType getArrayExprType(JmmNode arrayExpr) {
        long dimensions = arrayExpr.getChildren().stream()
                .filter(child -> !JmmKind.ARGS.check(child))
                .count();

        var dim = (int) Math.max(1, dimensions);
        return new JmmArrayType(JmmPrimitiveType.INT, dim);
    }

    private JmmType getNegExprType(JmmNode negExpr) {
        var expr = negExpr.getChild(0);
        var exprType = getExprType(expr);

        if (exprType == null) return null;

        if (!exprType.equals(JmmPrimitiveType.BOOLEAN)) return null;

        return exprType;
    }

    private JmmType getParentesesExprType(JmmNode parentesesExpr) {
        var expr = parentesesExpr.getChild(0);
        var exprType = getExprType(expr);

        return exprType;
    }

    private JmmType getThisExprType(JmmNode thisExpr) {
        return new JmmClassType(table.getFullyQualifiedName(), false, false);
    }

    private JmmType getArrayAccessExprType(JmmNode arrayAccessExpr) {
        var arrayType = getExprType(arrayAccessExpr.getChild(0));
        var indexType = getExprType(arrayAccessExpr.getChild(1));

        if (!(arrayType instanceof JmmArrayType jmmArrayType) || !intType().equals(indexType)) return null;

        if (jmmArrayType.dimension() <= 1) return jmmArrayType.itemType();

        return new JmmArrayType(jmmArrayType.itemType(), jmmArrayType.dimension() - 1);
    }

    private JmmType getLengthExprType(JmmNode lengthExpr) {
        var arrayType = getExprType(lengthExpr.getChild(0));

        if (!(arrayType instanceof JmmArrayType)) return null;

        return intType();
    }

    private JmmType getUnaryExprType(JmmNode unaryExpr) {
        var expr = unaryExpr.getChild(0);
        var exprType = getExprType(expr);

        if(!intType().equals(exprType)) return null;

        return intType();
    }

    private JmmType getFunctionCallExprType(JmmNode functionCallExpr) {
        var methodName = functionCallExpr.get(JmmAttributes.FUNCTION_CALL.NAME);
        var argTypes = getArgTypes(functionCallExpr);
        var signature = new Signature(methodName, argTypes);

        return getMethodFromCurrentOrSuper(signature)
                .map(MethodSymbol::returnType)
                .orElse(null);
    }

    private JmmType normalizeImplicitClassType(JmmType type) {
        if (!(type instanceof JmmClassType classType)) {
            return type;
        }

        var fqName = classType.fullyQualifiedName();
        var simpleName = fqName.substring(fqName.lastIndexOf('.') + 1);

        if (!table.isImplicitImport(simpleName)) {
            return type;
        }

        return new JmmClassType(simpleName, false, classType.staticRef());
    }

    private List<JmmType> getArgTypes(JmmNode callNode) {
        var argsNodes = callNode.getChildren(JmmKind.ARGS);
        if (argsNodes.isEmpty()) {
            return List.of();
        }

        return argsNodes.getFirst().getChildren().stream()
                .map(this::getExprType)
                .toList();
    }

    private Optional<MethodSymbol> getMethodFromCurrentOrSuper(Signature signature) {
        var current = table.getMethod(signature);
        if (current.isPresent()) {
            return current;
        }

        var superName = table.getSuper();
        if (superName == null) {
            return Optional.empty();
        }

        var maybeSuperTable = table.getImportedSymbolTable(superName);
        if (maybeSuperTable.isEmpty()) {
            var simpleSuper = superName.substring(superName.lastIndexOf('.') + 1);
            maybeSuperTable = table.getImplicitImport(simpleSuper);
        }

        if (maybeSuperTable.isEmpty()) {
            return Optional.empty();
        }

        return maybeSuperTable.get().getMethod(signature);
    }

    private Optional<SymbolTable> resolveTableFromReceiverType(JmmType receiverType) {
        if (!(receiverType instanceof JmmClassType classType)) return Optional.empty();

        var fqName = classType.fullyQualifiedName();
        var simpleName = fqName.substring(fqName.lastIndexOf('.') + 1);

        if (simpleName.equals(table.getClassName()) || fqName.equals(table.getFullyQualifiedName())) {
            return Optional.of(table);
        }

        var imported = table.getImportedSymbolTable(fqName);
        if (imported.isPresent()) return imported;

        imported = table.getImportedSymbolTable(simpleName);
        if (imported.isPresent()) return imported;

        return table.getImplicitImport(simpleName);
    }
}
