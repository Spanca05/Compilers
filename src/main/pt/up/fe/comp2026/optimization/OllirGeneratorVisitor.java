package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

import java.util.stream.Collectors;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    private MethodSymbol currentMethod;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table, ollirTypes);
        currentMethod = null;
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(PACKAGE_DECL, this::visitPackageDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::simpleVarDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
//        setDefaultVisit(this::defaultVisit);
    }


    private String simpleVarDecl(JmmNode varDecl, Void unused) {
        return ollirTypes.sanitizeId(varDecl.get("name")) + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class)) + ";";
    }

    private String visitParam(JmmNode varDecl, Void unused) {
        return ollirTypes.sanitizeId(varDecl.get("name")) + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class));
    }



    private String visitPackageDecl(JmmNode packageDecl, Void unused) {
        return "package " + String.join(".", packageDecl.getObjectAsList("path", String.class)) + ";\n";
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        var varName = node.get(JmmAttributes.ASSIGN_STMT.VAR);
        var code = new StringBuilder();

        if(isArrayStore(node)) {
            int numIndexes = node.getNumChildren() - 1;
            var value = exprVisitor.visit(node.getChild(numIndexes));

            var baseType = getVarType(varName);
            var targetType = getArrayIndexedType(baseType, numIndexes);
            var baseTypeStr = ollirTypes.toOllirType(baseType);
            var targetTypeStr = ollirTypes.toOllirType(targetType);
            var intTypeStr = ollirTypes.toOllirType(TypeUtils.intType());

            var lhs = new StringBuilder(ollirTypes.sanitizeId(varName)).append(baseTypeStr);

            for (int i = 0; i < numIndexes; i++) {
                var index = exprVisitor.visit(node.getChild(i));
                code.append(index.getComputation());

                var indexTemp = ollirTypes.nextTemp() + intTypeStr;
                code.append(indexTemp).append(SPACE).append(ASSIGN).append(intTypeStr)
                    .append(SPACE).append(index.getCode()).append(END_STMT);

                lhs.append("[").append(indexTemp).append("]");
            }

            code.append(value.getComputation());
            code.append(lhs).append(targetTypeStr).append(SPACE).append(ASSIGN)
                .append(targetTypeStr).append(SPACE).append(value.getCode()).append(END_STMT);

            return code.toString();
        }

        // Simple assignment: x = value;
        var rhs = exprVisitor.visit(node.getChild(0));
        var lhsType = getVarType(varName);
        var lhsTypeStr = ollirTypes.toOllirType(lhsType);

        code.append(rhs.getComputation());
        code.append(ollirTypes.sanitizeId(varName)).append(lhsTypeStr).append(SPACE)
            .append(ASSIGN).append(lhsTypeStr).append(SPACE)
            .append(rhs.getCode()).append(END_STMT);

        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {
        JmmType retType = currentMethod.returnType();
        var retTypeString = ollirTypes.toOllirType(retType);

        var code = new StringBuilder();

        if (node.getNumChildren() == 0) {
            code.append("ret");
            code.append(retTypeString);
            code.append(END_STMT);
            return code.toString();
        }

        var expr = exprVisitor.visit(node.getChild(0));

        code.append(expr.getComputation());
        code.append("ret");
        code.append(retTypeString);
        code.append(SPACE);
        code.append(expr.getCode());
        code.append(END_STMT);

        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        currentMethod = table.getMethod(TypeUtils.with(table).getMethodDeclSignature(node)).orElseThrow();

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if (node.getObject("isStatic", Boolean.class)) {
            code.append("static ");
        }

        // name
        var name = ollirTypes.sanitizeId(node.get("name"));
        code.append(name);

        // params
        var params = node.getChildren(PARAM);
        var paramsCode = params.stream()
                        .map(param -> visit(param))
                                .collect(Collectors.joining(", "));
        code.append("(" + paramsCode + ")");

        // type
        var retType = ollirTypes.toOllirType(currentMethod.returnType());//table.getReturnType(node.get("name")));
        code.append(retType);
        code.append(L_BRACKET);

        var stmts = node.getChildren(STMT);
        if (!stmts.isEmpty()) {
            // rest of its children stmts
            var stmtsCode = stmts.stream().map(this::visit).collect(Collectors.joining("\n   ", "   ", ""));
            code.append(stmtsCode);
        }

        code.append(R_BRACKET);
        code.append(NL);

        currentMethod = null;

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);


        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {
        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream().map(this::visit).forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }

    private JmmType getVarType(String varName) {
        var local = currentMethod.getLocalVariable(varName);
        if (local.isPresent()) return local.get().type();

        var param = currentMethod.getParameter(varName);
        if (param.isPresent()) return param.get().type();

        var field = table.getField(varName);
        if (field.isPresent()) return field.get().type();

        throw new RuntimeException("Variable not found: " + varName);
    }

    private boolean isArrayStore(JmmNode assignNode) {
        return assignNode.getNumChildren() > 1;
    }

    private JmmType getArrayIndexedType(JmmType baseType, int numIndexes) {
        var currentType = baseType;

        for (int i = 0; i < numIndexes; i++) {
            if (!(currentType instanceof JmmArrayType arrayType)) {
                throw new RuntimeException("Expected array type, got: " + currentType);
            }

            currentType = arrayType.dimension() == 1
                ? arrayType.itemType()
                : new JmmArrayType(arrayType.itemType(), arrayType.dimension() - 1);
        }

        return currentType;
    }
}
