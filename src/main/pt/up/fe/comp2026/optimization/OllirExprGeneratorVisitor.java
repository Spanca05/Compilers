package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;
    public MethodSymbol currentMethod;

    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils ollirTypes) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = ollirTypes; // We need to pass ollirTypes, to ensure labels are unique
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrAccessExpr);
        addVisit(PARENTESES_EXPR, this::visitParenthesesExpr);
        addVisit(ARRAY_EXPR, this::visitArrayExpr);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(FUNCTION_CALL, this::visitFunctionCall);
        addVisit(THIS_EXPR, this::visitThisExpr);
        addVisit(NEG_EXPR, this::visitNegExpr);
        addVisit(NEW_CLASS_EXPR, this::visitNewClassExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.intType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var boolType = TypeUtils.booleanType();
        String ollirBoolType = ollirTypes.toOllirType(boolType);
        String code = node.get("value") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var op = node.get("op");

        if (op.equals("&&")) {
            return visitAndExpr(node, unused);
        }

        if (op.equals("||")) {
            return visitOrExpr(node, unused);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        JmmType resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        JmmType type = types.getExprType(node);
        computation.append(op).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitAndExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));

        var boolType = ".bool";
        var temp = ollirTypes.nextTemp() + boolType;

        var trueLabel = ollirTypes.nextTemp("and_true_");
        var endLabel = ollirTypes.nextTemp("and_end_");

        var computation = new StringBuilder();

        computation.append(lhs.getComputation());

        computation.append("if (")
                .append(lhs.getCode())
                .append(") goto")
                .append(SPACE)
                .append(trueLabel)
                .append(END_STMT);

        computation.append(temp)
                .append(SPACE)
                .append(ASSIGN)
                .append(boolType)
                .append(SPACE)
                .append("false")
                .append(boolType)
                .append(END_STMT);

        computation.append("goto")
                .append(SPACE)
                .append(endLabel)
                .append(END_STMT);

        computation.append(trueLabel)
                .append(":")
                .append("\n");

        var rhs = visit(node.getChild(1));
        computation.append(rhs.getComputation());

        computation.append(temp)
                .append(SPACE)
                .append(ASSIGN)
                .append(boolType)
                .append(SPACE)
                .append(rhs.getCode())
                .append(END_STMT);

        computation.append(endLabel)
                .append(":")
                .append("\n");

        return new OllirExprResult(temp, computation.toString());
    }

    private OllirExprResult visitOrExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));

        var boolType = ".bool";
        var temp = ollirTypes.nextTemp() + boolType;

        var trueLabel = ollirTypes.nextTemp("or_true_");
        var endLabel = ollirTypes.nextTemp("or_end_");

        var computation = new StringBuilder();

        computation.append(lhs.getComputation());

        computation.append("if (")
                .append(lhs.getCode())
                .append(") goto")
                .append(SPACE)
                .append(trueLabel)
                .append(END_STMT);

        var rhs = visit(node.getChild(1));
        computation.append(rhs.getComputation());

        computation.append(temp)
                .append(SPACE)
                .append(ASSIGN)
                .append(boolType)
                .append(SPACE)
                .append(rhs.getCode())
                .append(END_STMT);

        computation.append("goto")
                .append(SPACE)
                .append(endLabel)
                .append(END_STMT);

        computation.append(trueLabel)
                .append(":")
                .append("\n");

        computation.append(temp)
                .append(SPACE)
                .append(ASSIGN)
                .append(boolType)
                .append(SPACE)
                .append("true")
                .append(boolType)
                .append(END_STMT);

        computation.append(endLabel)
                .append(":")
                .append("\n");

        return new OllirExprResult(temp, computation.toString());
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = ollirTypes.sanitizeId(node.get("name"));
        JmmType type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        if (table.getField(id).isPresent()
                && currentMethod.getLocalVariable(id).isEmpty()
                && currentMethod.getParameter(id).isEmpty()) {
            var temp = ollirTypes.nextTemp();
            String computation = temp + ollirType
                    + SPACE + ASSIGN + ollirType
                    + SPACE + "getfield(this, " + id + ollirType + ")" + ollirType
                    + END_STMT;
            return new OllirExprResult(temp + ollirType, computation);
        }

        String code = id + ollirType;

        return new OllirExprResult(code);

    }

    private OllirExprResult visitArrAccessExpr(JmmNode node, Void unused) {
        var arrayResult = visit(node.getChild(0));
        var indexResult = visit(node.getChild(1));
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var temp = ollirTypes.nextTemp();
        var resultType = ollirTypes.toOllirType(types.getExprType(node));

        String access = arrayResult.getCode()
                + "["
                + indexResult.getCode()
                + "]"
                + resultType;

        computation.append(arrayResult.getComputation()).append(indexResult.getComputation()).append(temp)
                .append(resultType).append(SPACE).append(ASSIGN).append(resultType).append(SPACE).append(access)
                .append(END_STMT);
        code.append(temp).append(resultType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitParenthesesExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitArrayExpr(JmmNode node, Void unused) {
        String ollirArrayType = ollirTypes.toOllirType(types.getExprType(node));
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        String ollirIntType = ollirTypes.toOllirType(TypeUtils.intType());
        // new int[a][b][c] {}
        var temp = ollirTypes.nextTemp();
        if (!node.getChild(0).isInstance(ARGS) && !node.getParent().isInstance(ARGS)) {
            code.append("new(array");
            for (var child : node.getChildren()) {
                var ollirChild = visit(child);
                code.append(",").append(ollirChild.getCode());
                computation.append(ollirChild.getComputation());
            }
            code.append(")").append(ollirArrayType);
        } // new int[]{a,b,c}
        else {
            var child = node.getChild(0);
            computation.append(temp).append(ollirArrayType).append(SPACE).append(ASSIGN)
                    .append(ollirArrayType).append(SPACE)
                    .append("new(array").append(",").append(child.getNumChildren()).append(ollirIntType)
                    .append(")").append(ollirArrayType).append(END_STMT);

            for (int i = 0; i < child.getNumChildren(); i++) {
                var ollirArg = visit(child.getChild(i));
                computation.append(temp).append(ollirArrayType).append("[").append(i).append(ollirIntType).append("]")
                        .append(ollirIntType)
                        .append(SPACE).append(ASSIGN).append(ollirIntType).append(SPACE).append(ollirArg.getCode())
                        .append(END_STMT);

            }
            code.append(temp).append(ollirArrayType);
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitLengthExpr(JmmNode node, Void unused) {
        var child = visit(node.getChild(0));
        var intType = TypeUtils.intType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String length = "arraylength(" + child.getCode() + ")" + ollirIntType;
        String temp = ollirTypes.nextTemp();
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        computation.append(child.getComputation());
        computation.append(temp).append(ollirIntType).append(SPACE).append(ASSIGN).append(ollirIntType).append(SPACE)
                .append(length).append(END_STMT);
        code.append(temp).append(ollirIntType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        String mName = node.get("name");
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var temp = ollirTypes.nextTemp();
        var receiverNode = node.getChild(0);
        var receiver = visit(receiverNode);

        computation.append(receiver.getComputation());

        String receiverCode = receiver.getCode();
        var method = table.getMethods(mName);

        if (method.isEmpty()) {
            var importer = Importer.fromThisClassPath();
            for (var impor : table.getImports()) {
                var impName = importer.loadImplicit(impor);
                if (impName.isPresent()) {
                    var impTable = importer.getSymbolTableOf(impName.get());
                    method = impTable.getMethods(mName);
                    if (!method.isEmpty()) {
                        break;
                    }
                }
            }
        }

        var ollirRetType = ollirTypes.toOllirType(method.getFirst().returnType());
        var argstr = new StringBuilder();

        if (node.getNumChildren() > 1) {
            var args = node.getChild(1);
            for (var arg : args.getChildren()) {
                var ollirArg = visit(arg);
                computation.append(ollirArg.getComputation());
                argstr.append(", ").append(ollirArg.getCode());
            }
        }
        var callType = method.getFirst().isStatic() ? "invokestatic(" : "invokevirtual(";
        if (method.getFirst().isStatic()) {
            receiverCode = receiverCode.substring(receiverCode.lastIndexOf(".") + 1);
        }
        computation.append(temp)
                .append(ollirRetType)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirRetType)
                .append(SPACE)
                .append(callType)
                .append(receiverCode)
                .append(", ")
                .append("\"")
                .append(mName)
                .append("\"")
                .append(argstr)
                .append(")")
                .append(ollirRetType)
                .append(END_STMT);

        code.append(temp).append(ollirRetType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitFunctionCall(JmmNode node, Void unused) {
        String mName = node.get("name");
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var temp = ollirTypes.nextTemp();
        var receiverCode = "this." + table.getClassName();
        var method = table.getMethods(mName);

        if (method.isEmpty()) {
            var importer = Importer.fromThisClassPath();
            for (var impor : table.getImports()) {
                var impName = importer.loadImplicit(impor);
                if (impName.isPresent()) {
                    var impTable = importer.getSymbolTableOf(impName.get());
                    method = impTable.getMethods(mName);
                    if (!method.isEmpty()) {
                        break;
                    }
                }
            }
        }

        var ollirRetType = ollirTypes.toOllirType(method.getFirst().returnType());
        var argstr = new StringBuilder();

        if (node.getNumChildren() > 0) {
            var args = node.getChild(0);
            for (var arg : args.getChildren()) {
                var ollirArg = visit(arg);
                computation.append(ollirArg.getComputation());
                argstr.append(", ").append(ollirArg.getCode());
            }
        }
        var callType = method.getFirst().isStatic() ? "invokestatic(" : "invokevirtual(";
        if (method.getFirst().isStatic()) {
            receiverCode = receiverCode.substring(receiverCode.lastIndexOf(".") + 1);
        }
        computation.append(temp)
                .append(ollirRetType)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirRetType)
                .append(SPACE)
                .append(callType)
                .append(receiverCode)
                .append(", ")
                .append("\"")
                .append(mName)
                .append("\"")
                .append(argstr)
                .append(")")
                .append(ollirRetType)
                .append(END_STMT);

        code.append(temp).append(ollirRetType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitThisExpr(JmmNode node, Void unused) {
        var thisType = ollirTypes.toOllirType(types.getExprType(node));
        return new OllirExprResult("this" + thisType);
    }

    private OllirExprResult visitNegExpr(JmmNode node, Void unused) {
        var expr = visit(node.getChild(0));

        var boolType = ".bool";
        var temp = ollirTypes.nextTemp() + boolType;

        var computation = new StringBuilder();
        computation.append(expr.getComputation());
        computation.append(temp)
                .append(ASSIGN)
                .append(boolType)
                .append(SPACE)
                .append("!.bool")
                .append(SPACE)
                .append(expr.getCode())
                .append(END_STMT);

        return new OllirExprResult(temp, computation.toString());
    }

    private OllirExprResult visitNewClassExpr(JmmNode node, Void unused) {
        var className = node.get(JmmAttributes.NEW_CLASS_EXPR.NAME);
        var classType = "." + className;
        var temp = ollirTypes.nextTemp();

        var code = temp + classType;

        var computation = new StringBuilder();

        computation.append(code)
                .append(SPACE)
                .append(ASSIGN)
                .append(classType)
                .append(SPACE)
                .append("new(")
                .append(className)
                .append(")")
                .append(classType)
                .append(END_STMT);

        computation.append("invokespecial(")
                .append(code)
                .append(", \"<init>\"");
        if (node.getNumChildren() >= 1) {
            for (var arg : node.getChild(0).getChildren()) {
                var ollirArg = visit(arg);
                computation.append(",").append(ollirArg.getCode());
            }

        }

        computation.append(").V").append(END_STMT);

        return new OllirExprResult(code, computation.toString());
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var operand = visit(node.getChild(0));
        var type = types.getExprType(node);
        var typeStr = ollirTypes.toOllirType(type);
        var temp = ollirTypes.nextTemp();
        var op = node.get("op");

        var code = new StringBuilder();
        var computation = new StringBuilder();
        computation.append(operand.getComputation());

        if (op.equals("-") || op.equals("+")) {
            computation.append(temp).append(typeStr)
                    .append(SPACE).append(ASSIGN).append(typeStr)
                    .append(SPACE).append("0").append(typeStr)
                    .append(SPACE).append(op).append(typeStr).append(SPACE)
                    .append(operand.getCode()).append(END_STMT);
            code.append(temp).append(typeStr);
        } else if (op.equals("++")) {
            computation.append(operand.getCode())
                    .append(SPACE).append(ASSIGN).append(typeStr)
                    .append(SPACE).append(operand.getCode())
                    .append(SPACE).append("+.i32").append(SPACE)
                    .append("1").append(typeStr).append(END_STMT);
            code.append(operand.getCode());
        } else if (op.equals("--")) {
            computation.append(operand.getCode())
                    .append(SPACE).append(ASSIGN).append(typeStr)
                    .append(SPACE).append(operand.getCode())
                    .append(SPACE).append("-.i32").append(SPACE)
                    .append("1").append(typeStr).append(END_STMT);
            code.append(operand.getCode());
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }
}
