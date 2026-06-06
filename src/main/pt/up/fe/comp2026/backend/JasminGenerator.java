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
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(ArrayOperand.class,this::generateArrayOperand);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtual);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecial);
        generators.put(NewInstruction.class, this::generateNew);
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

        // generate a single constructor method
        var defaultConstructor = """
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);

        code.append(defaultConstructor).append(NL);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        currentMethod = method;

        // Initialize utils, to have fresh labels
        utils = new OptUtils(null);
        stackDepth = 0;
        maxStackDepth = 0;

        var code = new StringBuilder();

        var modifier = "";
        if (method.getMethodAccessModifier().equals(AccessModifier.PUBLIC))
            modifier = types.getModifier(AccessModifier.PUBLIC);
        if (method.getMethodAccessModifier().equals(AccessModifier.PRIVATE))
            modifier = types.getModifier(AccessModifier.PRIVATE);
        if (method.getMethodAccessModifier().equals(AccessModifier.PROTECTED))
            modifier = types.getModifier(AccessModifier.PROTECTED);

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

                code.append("iastore").append(NL);
                stackPop(3);
            } else {
                var reg = currentMethod.getVarTable().get(operand.getName());
                code.append(apply(assign.getRhs()));
                code.append(types.getStore(reg)).append(NL);
                stackPop(1);
            }


            return code.toString();
        } finally {
            isInsideAssignment = false;
        }
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        stackPush(1);
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
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

        code.append("iaload").append(NL);
        stackPop(1);

        return code.toString();
    }


    private String generateBinaryOp(BinaryOpInstruction binaryOp) {

        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));


        var typePrefix = types.getTypePrefix(binaryOp.getOperation().getTypeInfo());

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(typePrefix).append(op).append(NL);
        stackPop(1); // pop 2, push 1 → net -1

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
        if (!isVoidType(inst.getReturnType())) stackPush(1);

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
            callerClass = superClass.get().replace('.', '/');
        } else {
            callerClass = types.getFullClassName(inst.getCaller().getType());
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

}