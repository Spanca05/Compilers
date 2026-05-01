package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.Kind;
import pt.up.fe.comp2026.ast.AccessType;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

import java.io.Console;
import java.util.List;
import java.util.stream.Collectors;

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
        addVisit(ARRAY_ACESS_EXPR, this::visitArrAccessExpr);
        addVisit(PARENTESES_EXPR, this::visitParenthesesExpr);
        addVisit(ARRAY_EXPR, this::visitArrayExpr);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(THIS_EXPR, this::visitThisExpr);
        addVisit(NEG_EXPR, this::visitNegExpr);
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


        String code = id + ollirType;

        return new OllirExprResult(code);

    }

    private OllirExprResult visitArrAccessExpr(JmmNode node, Void unused) {
        var arrayResult = visit(node.getChild(0));
        var indexResult = visit(node.getChild(1));

        var temp = ollirTypes.nextTemp();
        var resultType = ollirTypes.toOllirType(types.getExprType(node));
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        String access = arrayResult.getCode()
                + "["
                + indexResult.getCode()
                + "]"
                + resultType;

        computation.append(arrayResult.getComputation()).append(indexResult.getComputation()).append(temp).
                append(resultType).append(SPACE).append(ASSIGN).append(resultType).append(SPACE).append(access).append(END_STMT);
        code.append(temp).append(resultType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitParenthesesExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitArrayExpr(JmmNode node, Void unused) {
        var child = visit(node.getChild(0));
        String ollirArrayType = ollirTypes.toOllirType(types.getExprType(node));
        String code = "new(array," + child.getCode() + ")" + ollirArrayType;
        String computation = child.getComputation();
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitLengthExpr(JmmNode node, Void unused) {
        var child = visit(node.getChild(0));
        var intType = TypeUtils.intType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String length = "arraylength(" + child.getCode() + ")" + ollirIntType;
        String temp = ollirTypes.nextTemp();
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        computation.append(temp).append(ollirIntType).append(SPACE).append(ASSIGN).append(ollirIntType).append(SPACE).append(length).append(END_STMT);
        code.append(temp).append(ollirIntType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        String mName = node.get("name");
        String refName = "";
        var temp = ollirTypes.nextTemp();
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        var method = table.getMethods(mName);
        var ollirRetType = ollirTypes.toOllirType(method.getFirst().returnType());
        var child = node.getChild(0);
        if(child.isInstance(THIS_EXPR)){
            refName = "this";
        }

        computation.append(temp).append(ollirRetType).append(SPACE).append(ASSIGN).append(ollirRetType).append(SPACE)
                .append("invokevirtual(").append(refName).append(",").append("\"").append(mName).append("\"");

        if(node.getNumChildren() > 1){
            var args = node.getChild(1);

            for (var arg : args.getChildren()){
                var ollirArg = visit(arg);
                computation.append(",").append(ollirArg.getCode());
            }
        }

        computation.append(")").append(ollirRetType).append(END_STMT);

        code.append(temp).append(ollirRetType);

        return new OllirExprResult(code.toString(),computation.toString());
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
}
