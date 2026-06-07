package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.ArrayType;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import org.specs.comp.ollir.type.ClassType;
import org.specs.comp.ollir.type.Type;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2026.optimization.OptUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.lang.annotation.ElementType;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.OperandType.INT32;
import static pt.up.fe.comp2026.JavammParser.*;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    private List<Report> reports;

    private String code;

    private Method currentMethod;
    private int labelCounter;
    boolean isInsideAssignment;

    private final JasminUtils types;
    private OptUtils utils;
    private final FunctionClassMap<TreeNode, String> generators;

    private int stackDepth;
    private int maxStackDepth;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        labelCounter = 0;
        isInsideAssignment = false;
        stackDepth = 0;
        maxStackDepth = 0;

        types = new JasminUtils(ollirResult);
        // Initialize everytime we start a method
        utils = null;
        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(ArrayOperand.class,this::generateArrayOperand);
        generators.put(Operand.class, this::generateOperand);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtual);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecial);
        generators.put(InvokeStaticInstruction.class, this::generateInvokeStatic);
        generators.put(NewInstruction.class, this::generateNew);
        generators.put(ArrayLengthInstruction.class, this::generateLength);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(PutFieldInstruction.class, this::generatePutField);
    }

    private void stackPush(int n) {
        stackDepth += n;
        if (stackDepth > maxStackDepth) maxStackDepth = stackDepth;
    }

    private void stackPop(int n) {
        stackDepth -= n;
    }

    private boolean isVoidType(Type type) {
        return type instanceof BuiltinType b && b.getKind() == BuiltinKind.VOID;
    }


    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var nameWithPackage = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        code.append(".class ").append("public ").append(nameWithPackage).append(NL);

        // TODO: When you support 'extends', this must be updated

        var fullSuperClass = "java/lang";
        var superClass = ollirResult.getOllirClass().getSuperClass();

        for (var importt : ollirResult.getOllirClass().getImports()) {
            if (importt.endsWith(superClass))
                fullSuperClass = importt.substring(0, importt.length() - superClass.length() - 1);
        }

        fullSuperClass += "/" + superClass;

        code.append(".super ").append(fullSuperClass).append(NL).append(NL);

        for (var field : classUnit.getFields()) {
            var modifier = types.getModifier(field.getFieldAccessModifier());
            var descriptor = types.getTypeDescriptor(field.getFieldType());
            code.append(".field ").append(modifier).append("'").append(field.getFieldName()).append("'").append(" ").append(descriptor).append(NL);
        }

        // generate all methods including the constructor
        for (var method : ollirResult.getOllirClass().getMethods()) {
            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        currentMethod = method;
        labelCounter = 0;
        // Initialize utils, to have fresh labels
        utils = new OptUtils(null);
        stackDepth = 0;
        maxStackDepth = 0;

        var code = new StringBuilder();

        var modifier = "";
        if (method.isConstructMethod()) {
            modifier = "public ";
        } else if (method.getMethodAccessModifier().equals(AccessModifier.PUBLIC)) {
            modifier = types.getModifier(AccessModifier.PUBLIC);
        } else if (method.getMethodAccessModifier().equals(AccessModifier.PRIVATE)) {
            modifier = types.getModifier(AccessModifier.PRIVATE);
        } else if (method.getMethodAccessModifier().equals(AccessModifier.PROTECTED)) {
            modifier = types.getModifier(AccessModifier.PROTECTED);
        }

        var staticMod = method.isStaticMethod() ? "static " : "";

        var methodName = method.getMethodName();

        var params = method.getParams().stream()
                .map(p -> types.getTypeDescriptor(p.getType()))
                .collect(Collectors.joining());

        var returnType = types.getTypeDescriptor(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(staticMod)
                .append(methodName)
                .append("(").append(params).append(")").append(returnType).append(NL);

        // Generate body first so stack/locals limits can be computed
        var bodyCode = new StringBuilder();

        for (var inst : method.getInstructions()) {
            var labels = currentMethod.getLabels(inst);
            for (var label : labels) {
                bodyCode.append(label).append(":").append(NL);
            }
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            bodyCode.append(instCode);
        }

        // Compute limits from var table and tracked stack depth
        var localsLimit = method.getVarTable().values().stream()
                .mapToInt(Descriptor::getVirtualReg)
                .max().orElse(-1) + 1;

        code.append(TAB).append(".limit stack ").append(maxStackDepth).append(NL);
        code.append(TAB).append(".limit locals ").append(localsLimit).append(NL);
        code.append(TAB).append(bodyCode);
        if (method.isConstructMethod()) {
            code.append(TAB).append("return").append(NL);
        }
        code.append(".end method\n");

        currentMethod = null;
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        try {
            isInsideAssignment = true;

            var code = new StringBuilder();
            var lhs = assign.getDest();
            var operand = (Operand) lhs;
            if (operand instanceof ArrayOperand arrayOperand){
                var reg = currentMethod.getVarTable().get(arrayOperand.getName());
                code.append(types.getLoad(reg)).append(NL);
                stackPush(1);

                var indexOperand =  arrayOperand.getIndexOperands().get(0);
                code.append(generators.apply(indexOperand)).append(NL);

                code.append(apply(assign.getRhs()));
                code.append(arrayOperand.getType() instanceof ArrayType ? "aastore" : "iastore").append(NL);
                stackPop(3);
            } else {
                var reg = currentMethod.getVarTable().get(operand.getName());
                code.append(apply(assign.getRhs()));
                if (!isVoidType(assign.getTypeOfAssign())) {
                    code.append(types.getStore(reg)).append(NL);
                    stackPop(1);
                }
            }


            return code.toString();
        } finally {
            isInsideAssignment = false;
        }
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOp) {
        var code = new StringBuilder();
        code.append(apply(singleOp.getCondition()));
        code.append("ifne ").append(singleOp.getLabel());
        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
    }

    private String generateLiteral(LiteralElement literal) {
        stackPush(1);
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        if (operand.getName().equals("true")) {
            stackPush(1);
            return "ldc 1" + NL;
        }
        if (operand.getName().equals("false")) {
            stackPush(1);
            return "ldc 0" + NL;
        }

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        stackPush(1);
        return types.getLoad(reg) + NL;
    }

    private String generateArrayOperand(ArrayOperand arrayOperand) {
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(arrayOperand.getName());
        code.append(types.getLoad(reg)).append(NL);
        stackPush(1);

        var indexOperand = arrayOperand.getIndexOperands().get(0);
        code.append(generators.apply(indexOperand));

        code.append(arrayOperand.getType() instanceof ArrayType ? "aaload" : "iaload").append(NL);
        stackPop(1);

        return code.toString();
    }

    private String generateOpCond(OpCondInstruction opCond) {
        var code = new StringBuilder();
        code.append(apply(opCond.getCondition()));
        code.append("ifne ").append(opCond.getLabel()).append(NL);
        stackPop(1);
        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {

        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(apply(unaryOp.getOperand()));

        var typePrefix = types.getTypePrefix(unaryOp.getOperation().getTypeInfo());

        // apply operation
        var op = switch (unaryOp.getOperation().getOpType()) {
            case LOGICAL_NOT -> {
                code.append("ldc 1").append(NL);
                stackPush(1);
                yield "xor";
            }
            default -> throw new NotImplementedException(unaryOp.getOperation().getOpType());
        };

        code.append(typePrefix).append(op).append(NL);
        stackPop(1); // pop 2, push 1 → net -1

        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {

        var opType = binaryOp.getOperation().getOpType();

        if (opType == OperationType.LTH || opType == OperationType.LTE || opType == OperationType.GTH ||
                opType == OperationType.GTE || opType == OperationType.EQ  || opType == OperationType.NEQ) {
            return generateComparison(binaryOp);
        }
        var code = new StringBuilder();
        // load values on the left and on the right
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var typePrefix = types.getTypePrefix(binaryOp.getOperation().getTypeInfo());
        // apply operation
        var op = switch (opType) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case REM -> "rem";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(typePrefix).append(op).append(NL);
        stackPop(1);

        return code.toString();
    }

    private String generateComparison(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var trueLabel = "cmp_true_" + labelCounter;
        var endLabel = "cmp_end_" + labelCounter++;

        var jump = switch (binaryOp.getOperation().getOpType()) {
            case LTH -> "if_icmplt";
            case LTE -> "if_icmple";
            case GTH -> "if_icmpgt";
            case GTE -> "if_icmpge";
            case EQ  -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            default  -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(jump).append(" ").append(trueLabel).append(NL);
        code.append("ldc 0").append(NL);
        code.append("goto ").append(endLabel).append(NL);
        code.append(trueLabel).append(":").append(NL);
        code.append("ldc 1").append(NL);
        code.append(endLabel).append(":").append(NL);
        stackPop(1);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        var returnType = returnInst.getReturnType();

        var typePrefix = types.getTypePrefix(returnType);

        // Load operand into the stack, if present
        returnInst.getOperand().ifPresent(op -> code.append(apply(op)));

        if (!isVoidType(returnType)) {
            stackPop(1); // ireturn/areturn pops 1
        }

        code.append(typePrefix).append("return").append(NL);

        return code.toString();
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction inst) {
        var code = new StringBuilder();

        code.append(apply(inst.getCaller()));
        for (var arg : inst.getArguments()) {
            code.append(apply(arg));
        }

        var callerClass = types.getFullClassName(inst.getCaller().getType());
        var methodName = ((LiteralElement) inst.getMethodName()).getLiteral().replace("\"", "");
        var paramDesc = inst.getArguments().stream()
                .map(a -> types.getTypeDescriptor(a.getType()))
                .collect(Collectors.joining());
        var retDesc = types.getTypeDescriptor(inst.getReturnType());

        code.append("invokevirtual ").append(callerClass).append("/")
                .append(methodName).append("(").append(paramDesc).append(")").append(retDesc).append(NL);

        stackPop(1 + inst.getArguments().size());
        if (!isVoidType(inst.getReturnType())) {
            stackPush(1);
            if (!isInsideAssignment) {
                code.append("pop").append(NL);
                stackPop(1);
            }
        }

        return code.toString();
    }

    private String generateInvokeSpecial(InvokeSpecialInstruction inst) {
        var code = new StringBuilder();

        code.append(apply(inst.getCaller()));
        for (var arg : inst.getArguments()) {
            code.append(apply(arg));
        }

        var superClass = inst.getSuperClass();
        String callerClass;
        if (superClass.isPresent()) {
            callerClass = types.resolveClassName(superClass.get());
        } else {
            callerClass = types.resolveClassName(types.getFullClassName(inst.getCaller().getType()));
        }

        var methodName = ((LiteralElement) inst.getMethodName()).getLiteral().replace("\"", "");
        var paramDesc = inst.getArguments().stream()
                .map(a -> types.getTypeDescriptor(a.getType()))
                .collect(Collectors.joining());
        var retDesc = types.getTypeDescriptor(inst.getReturnType());

        code.append("invokespecial ").append(callerClass).append("/")
                .append(methodName).append("(").append(paramDesc).append(")").append(retDesc).append(NL);

        stackPop(1 + inst.getArguments().size());
        if (!isVoidType(inst.getReturnType())) stackPush(1);

        return code.toString();
    }

    private String generateInvokeStatic(InvokeStaticInstruction inst) {
        var code = new StringBuilder();

        for (var arg : inst.getArguments()) {
            code.append(apply(arg));
        }

        var callerClass = types.getFullClassName(inst.getCaller().getType());
        var methodName = ((LiteralElement) inst.getMethodName()).getLiteral().replace("\"", "");
        var paramDesc = inst.getArguments().stream()
                .map(a -> types.getTypeDescriptor(a.getType()))
                .collect(Collectors.joining());
        var retDesc = types.getTypeDescriptor(inst.getReturnType());

        code.append("invokestatic ").append(callerClass).append("/")
                .append(methodName).append("(").append(paramDesc).append(")").append(retDesc).append(NL);

        stackPop(inst.getArguments().size());
        if (!isVoidType(inst.getReturnType())) {
            stackPush(1);
            if (!isInsideAssignment) {
                code.append("pop").append(NL);
                stackPop(1);
            }
        }

        return code.toString();
    }

    private String generateNew(NewInstruction inst) {
        var code = new StringBuilder();
        var retType = inst.getReturnType();
        if (retType instanceof ArrayType arrayType) {
            var elementType = types.getTypeDescriptor(arrayType.getElementType());
            var numDims = arrayType.getNumDimensions();
            var args = inst.getArguments();
            for (var arg : args) {
                code.append(apply(arg));
            }
            stackPush(1);
            if (args.size() > 1){
                code.append("multianewarray ");
                for(int i = 0; i < numDims; i++){
                    code.append("[");
                }
                code.append(elementType).append(" ").append(numDims).append(" ").append(NL);
                return code.toString();
            } else {
                return code.toString() + "newarray " + "int" + NL;
            }
        } else {
            var className = types.getFullClassName(inst.getReturnType());
            stackPush(1);
            return "new " + className + NL;
        }
    }

    public static String getTypeDescriptor(Type type) {
        return switch(type.toString()) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "VOID" -> "V";
            default -> throw new RuntimeException(
                    "Not implemented for element type '" +
                            type.toString() + "'");
        };
    }

    private String generateGetField(GetFieldInstruction inst) {
        var code = new StringBuilder();

        code.append(apply(inst.getObject()));

        var className = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        var fieldName = inst.getField().getName();
        var fieldDescriptor = types.getTypeDescriptor(inst.getFieldType());

        code.append("getfield ").append(className).append("/").append(fieldName)
                .append(" ").append(fieldDescriptor).append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction inst) {
        var code = new StringBuilder();

        code.append(apply(inst.getObject()));
        code.append(apply(inst.getValue()));

        var className = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        var fieldName = inst.getField().getName();
        var fieldDescriptor = types.getTypeDescriptor(inst.getField().getType());

        code.append("putfield ").append(className).append("/").append(fieldName)
                .append(" ").append(fieldDescriptor).append(NL);

        stackPop(2);

        return code.toString();
    }

    private String generateLength(ArrayLengthInstruction arrayLength) {
        var code = new StringBuilder();

        code.append(apply(arrayLength.getOperands().get(0)));
        code.append("arraylength").append(NL);

        return code.toString();
    }
}