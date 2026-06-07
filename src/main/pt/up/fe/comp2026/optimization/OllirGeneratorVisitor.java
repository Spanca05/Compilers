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

import java.util.List;
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
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(FOR_STMT, this::visitForStmt);
        addVisit(DO_WHILE_STMT, this::visitDoWhileStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
    }

    private String simpleVarDecl(JmmNode varDecl, Void unused) {
        return ollirTypes.sanitizeId(varDecl.get("name"))
                + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class)) + ";";
    }

    private String visitParam(JmmNode varDecl, Void unused) {
        return ollirTypes.sanitizeId(varDecl.get("name"))
                + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class));
    }

    private String visitPackageDecl(JmmNode packageDecl, Void unused) {
        return "package " + String.join(".", packageDecl.getObjectAsList("path", String.class)) + ";\n";
    }

    private String visitImportDecl(JmmNode importDecl, Void unused) {
        var path = String.join(".", importDecl.getObjectAsList("path", String.class));
        return "import " + path + ";\n";
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var varName = node.get(JmmAttributes.ASSIGN_STMT.VAR);
        var code = new StringBuilder();

        if (isArrayStore(node)) {
            int numIndexes = node.getNumChildren() - 1;
            var value = exprVisitor.visit(node.getChild(numIndexes));
            var baseType = getVarType(varName);
            var targetType = getArrayIndexedType(baseType, numIndexes);
            var baseTypeStr = ollirTypes.toOllirType(baseType);
            var targetTypeStr = ollirTypes.toOllirType(targetType);
            ollirTypes.toOllirType(TypeUtils.intType());

            var lhs = new StringBuilder(ollirTypes.sanitizeId(varName)).append(baseTypeStr);

            for (int i = 1; i < numIndexes; i++) {
                var index = exprVisitor.visit(node.getChild(i - 1));
                code.append(index.getComputation());

                var innerType = getArrayIndexedType(baseType, i);
                var innerTypeStr = ollirTypes.toOllirType(innerType);
                var arrayTemp = ollirTypes.nextTemp() + innerTypeStr;
                code.append(arrayTemp).append(SPACE).append(ASSIGN).append(innerTypeStr)
                        .append(SPACE).append(lhs)
                        .append("[").append(index.getCode()).append("]")
                        .append(innerTypeStr).append(END_STMT);

                lhs = new StringBuilder(arrayTemp);
            }

            var lastIndex = exprVisitor.visit(node.getChild(numIndexes - 1));
            code.append(lastIndex.getComputation());
            code.append(value.getComputation());

            code.append(lhs)
                    .append("[").append(lastIndex.getCode()).append("]")
                    .append(targetTypeStr).append(SPACE).append(ASSIGN)
                    .append(targetTypeStr).append(SPACE).append(value.getCode()).append(END_STMT);

            return code.toString();
        }

        // Simple assignment: x = value;
        var rhs = exprVisitor.visit(node.getChild(0));
        var lhsType = getVarType(varName);
        var lhsTypeStr = ollirTypes.toOllirType(lhsType);

        code.append(rhs.getComputation());
        if (table.getField(varName).isPresent()
                && currentMethod.getLocalVariable(varName).isEmpty()
                && currentMethod.getParameter(varName).isEmpty()) {
            var rhsCode = rhs.getCode();
            if (!isSimpleArg(rhsCode)) {
                var tmp = ollirTypes.nextTemp() + lhsTypeStr;
                code.append(tmp).append(SPACE).append(ASSIGN).append(lhsTypeStr)
                        .append(SPACE).append(rhsCode).append(END_STMT);
                rhsCode = tmp;
            }
            code.append("putfield(this, ")
                    .append(ollirTypes.sanitizeId(varName)).append(lhsTypeStr)
                    .append(", ").append(rhsCode).append(").V")
                    .append(END_STMT);
        } else {
            code.append(ollirTypes.sanitizeId(varName)).append(lhsTypeStr).append(SPACE)
                    .append(ASSIGN).append(lhsTypeStr).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);
        }

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
        exprVisitor.currentMethod = currentMethod;

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        if (isPublic) {
            code.append("public ");
        } else if (node.hasAttribute("visibility")) {
            var visibility = node.get("visibility");
            code.append(visibility).append(" ");
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
        var retType = ollirTypes.toOllirType(currentMethod.returnType());// table.getReturnType(node.get("name")));
        code.append(retType);
        code.append(L_BRACKET);

        var stmts = node.getChildren(STMT);
        if (!stmts.isEmpty()) {
            // rest of its children stmts
            var stmtsCode = stmts.stream().map(this::visit).collect(Collectors.joining("\n   ", "   ", ""));
            code.append(stmtsCode);
        }

        if (node.getChildren(RETURN_STMT).isEmpty()) {
            code.append("ret.V;\n");
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

        if (node.hasAttribute(JmmAttributes.CLASS_DECL.SUPERCLASS)) {
            var superClass = table.getSuper();

            code.append(" extends ")
                    .append(superClass);
        }

        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);

        for (var field : table.getFields()) {
            code.append(".field public ")
                    .append(field.name())
                    .append(ollirTypes.toOllirType(field.type()))
                    .append(END_STMT);
        }
        code.append(NL);

        code.append(buildConstructor(node.getChildren(VAR_DECL)));
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        var cond = exprVisitor.visit(node.getChild(0));
        var trueLabel = ollirTypes.nextTemp("if_true_");
        var exitLabel = ollirTypes.nextTemp("if_exit_");

        var code = new StringBuilder();

        code.append(cond.getComputation());

        code.append("if (")
                .append(cond.getCode())
                .append(") goto ")
                .append(trueLabel)
                .append(END_STMT);

        if (node.getNumChildren() > 2) {
            code.append(visit(node.getChild(2)));
        }

        code.append("goto")
                .append(SPACE)
                .append(exitLabel)
                .append(END_STMT);

        var ifBlock = visit(node.getChild(1));

        code.append(trueLabel)
                .append(":")
                .append(NL);

        code.append(ifBlock);

        code.append(exitLabel)
                .append(":")
                .append(NL);

        return code.toString();
    }

    private String visitBlockStmt(JmmNode node, Void unused) {
        var code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        var cond = exprVisitor.visit(node.getChild(0));
        var loopLabel = ollirTypes.nextTemp("loop_body_");
        var beginLabel = ollirTypes.nextTemp("loop_begin_");
        var exitLabel = ollirTypes.nextTemp("loop_exit_");

        var code = new StringBuilder();

        code.append(beginLabel)
                .append(":")
                .append(NL);

        code.append(cond.getComputation());

        code.append("if (")
                .append(cond.getCode())
                .append(") goto ")
                .append(loopLabel)
                .append(END_STMT);

        code.append("goto")
                .append(SPACE)
                .append(exitLabel)
                .append(END_STMT);

        code.append(loopLabel)
                .append(":")
                .append(NL);

        var whileBlock = visit(node.getChild(1));

        code.append(whileBlock);

        code.append("goto")
                .append(SPACE)
                .append(beginLabel)
                .append(END_STMT);

        code.append(exitLabel)
                .append(":")
                .append(NL);

        return code.toString();
    }

    private String visitForStmt(JmmNode node, Void unused) {
        if (node.getNumChildren() == 2) {
            // for as while
            var checkCond = exprVisitor.visit(node.getChild(0));
            var block = visit(node.getChild(1));

            var beginLabel = ollirTypes.nextTemp("loop_begin_");
            var exitLabel = ollirTypes.nextTemp("loop_exit_");
            var bodyLabel = ollirTypes.nextTemp("loop_body_");

            var code = new StringBuilder();
            code.append(beginLabel).append(":").append(NL);
            code.append(checkCond.getComputation());
            code.append("if (").append(checkCond.getCode()).append(") goto ").append(bodyLabel).append(END_STMT);
            code.append("goto ").append(exitLabel).append(END_STMT);
            code.append(bodyLabel).append(":").append(NL);
            code.append(block);
            code.append("goto ").append(beginLabel).append(END_STMT);
            code.append(exitLabel).append(":").append(NL);

            return code.toString();
        }
        var varName = node.get("var");
        var varType = getVarType(varName);
        var varTypeStr = ollirTypes.toOllirType(varType);

        var checkCond = exprVisitor.visit(node.getChild(1));
        var updateCond = exprVisitor.visit(node.getChild(2));

        var loopLabel = ollirTypes.nextTemp("loop_body_");
        var beginLabel = ollirTypes.nextTemp("loop_begin_");
        var exitLabel = ollirTypes.nextTemp("loop_exit_");

        var code = new StringBuilder();

        var initNode = node.getChild(0);
        if (initNode.getKind() == ASSIGN_STMT) {
            code.append(visit(initNode));
        } else {
            var initExpr = exprVisitor.visit(initNode);

            code.append(initExpr.getComputation());

            code.append(ollirTypes.sanitizeId(varName))
                    .append(varTypeStr)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(varTypeStr)
                    .append(SPACE)
                    .append(initExpr.getCode())
                    .append(END_STMT);
        }

        code.append(beginLabel)
                .append(":")
                .append(NL);

        code.append(checkCond.getComputation());

        code.append("if (")
                .append(checkCond.getCode())
                .append(") goto ")
                .append(loopLabel)
                .append(END_STMT);

        code.append("goto")
                .append(SPACE)
                .append(exitLabel)
                .append(END_STMT);

        code.append(loopLabel)
                .append(":")
                .append(NL);

        var forBlock = visit(node.getChild(3));

        code.append(forBlock);

        code.append(updateCond.getComputation());

        code.append(ollirTypes.sanitizeId(varName))
                .append(varTypeStr)
                .append(SPACE)
                .append(ASSIGN)
                .append(varTypeStr)
                .append(SPACE)
                .append(updateCond.getCode())
                .append(END_STMT);

        code.append("goto")
                .append(SPACE)
                .append(beginLabel)
                .append(END_STMT);

        code.append(exitLabel)
                .append(":")
                .append(NL);

        return code.toString();
    }

    private String visitDoWhileStmt(JmmNode node, Void unused) {
        var doBlock = visit(node.getChild(0));
        var cond = exprVisitor.visit(node.getChild(1));
        var beginLabel = ollirTypes.nextTemp("loop_begin_");

        var code = new StringBuilder();

        code.append(beginLabel)
                .append(":")
                .append(NL);

        code.append(doBlock);

        code.append(cond.getComputation());

        code.append("if (")
                .append(cond.getCode())
                .append(") goto ")
                .append(beginLabel)
                .append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var expr = exprVisitor.visit(node.getChild(0));
        var code = new StringBuilder();

        code.append(expr.getComputation());

        return code.toString();
    }

    private String buildConstructor(List<JmmNode> children) {
        var code = new StringBuilder();
        code.append("""
                .construct "%s"().V {
                    invokespecial(this."%s", "<init>").V;""".formatted("<init>",
                table.getSuper() != null ? table.getSuper() : "java.lang.Object"));

        for (var fieldNode : children) {
            if (fieldNode.getNumChildren() > 1) {
                var valueResult = exprVisitor.visit(fieldNode.getChild(1));
                var fieldName = fieldNode.get("name");
                var fieldType = ollirTypes.toOllirType(table.getField(fieldName).get().type());

                code.append("\n   ").append(valueResult.getComputation());
                var valueCode = valueResult.getCode();
                if (!isSimpleArg(valueCode)) {
                    var tmp = ollirTypes.nextTemp() + fieldType;
                    code.append(tmp).append(SPACE).append(ASSIGN).append(fieldType)
                            .append(SPACE).append(valueCode).append(END_STMT);
                    valueCode = tmp;
                }
                code.append("putfield(this, ")
                        .append(fieldName).append(fieldType)
                        .append(", ").append(valueCode)
                        .append(").V").append(END_STMT);
            }
        }

        code.append("\n}");
        return code.toString();
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream().map(this::visit).forEach(code::append);

        return code.toString();
    }

    private JmmType getVarType(String varName) {
        var local = currentMethod.getLocalVariable(varName);
        if (local.isPresent())
            return local.get().type();

        var param = currentMethod.getParameter(varName);
        if (param.isPresent())
            return param.get().type();

        var field = table.getField(varName);
        if (field.isPresent())
            return field.get().type();

        throw new RuntimeException("Variable not found: " + varName);
    }

    private boolean isArrayStore(JmmNode assignNode) {
        return assignNode.getNumChildren() > 1;
    }

    private boolean isSimpleArg(String code) {
        if (code == null || code.isEmpty())
            return false;
        return !(code.contains("(") || code.contains("["));
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
