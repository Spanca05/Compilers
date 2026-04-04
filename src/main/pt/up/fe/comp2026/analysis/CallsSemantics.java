package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import java.util.List;
import java.util.Optional;

public class CallsSemantics extends AnalysisVisitor {
    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.FUNCTION_CALL, this::visitFunctionCall);
        addVisit(JmmKind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(JmmKind.NEW_CLASS_EXPR, this::visitNewClassExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        //System.out.println(signature);

        currentMethod = table.getMethod(signature).orElseThrow();
        return null;
    }

    public Void visitFunctionCall(JmmNode functionCall, SymbolTable table) {
        var methodName = functionCall.get(JmmAttributes.METHOD_CALL_EXPR.NAME);

        var argTypes = getArgTypes(functionCall, table);

        var signature = new Signature(methodName, argTypes);

        if (!existsInCurrentClassOrSuperclass(signature, table)) {
            var message = "No method is declared with this signature.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(functionCall),
                    NodeUtils.getColumn(functionCall),
                    message,
                    null
            ));
        }

        return null;
    }

    public Void visitMethodCallExpr(JmmNode methodCall, SymbolTable table) {
        var methodName = methodCall.get(JmmAttributes.METHOD_CALL_EXPR.NAME);

        var argTypes = getArgTypes(methodCall, table);

        var signature = new Signature(methodName, argTypes);

        var receiver = methodCall.getChild(0);

        if (JmmKind.THIS_EXPR.check(receiver)) {
            if (!existsInCurrentClassOrSuperclass(signature, table)) {
                var message = String.format(
                        "No matching method '%s' in current class/superclass with argument types %s.",
                        methodName,
                        argTypes
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall),
                        NodeUtils.getColumn(methodCall),
                        message,
                        null
                ));
            }

            return null;
        }

        var receiverType = TypeUtils.with(table).getExprType(receiver);
        var targetTable = resolveTableFromReceiverType(receiverType, table);

        if (targetTable.isEmpty() || targetTable.get().getMethod(signature).isEmpty()) {
            var receiverName = receiverType instanceof JmmClassType classType
                ? classType.fullyQualifiedName()
                : receiverType.toString();
            var message = String.format(
                "No matching method '%s' for receiver type '%s' with argument types %s.",
                methodName,
                receiverName,
                argTypes
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall),
                    NodeUtils.getColumn(methodCall),
                    message,
                    null
            ));
        }

        return null;
    }

    public Void visitNewClassExpr(JmmNode newClassExpr, SymbolTable table) {
        var className = newClassExpr.get("name");

        var isCurrentClass = className.equals(table.getClassName());
        var isExplicitImport = table.getImportedFullyQualifiedName(className).isPresent();
        var isImplicitImport = ((JmmSymbolTable) table).isImplicitImport(className);

        if (!(isCurrentClass || isExplicitImport || isImplicitImport)) {
            var message = String.format("Class '%s' is not imported.", className);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newClassExpr),
                    NodeUtils.getColumn(newClassExpr),
                    message,
                    null
            ));
        }

        var argTypes = getArgTypes(newClassExpr, table);
        var constructorSignature = new Signature("<init>", argTypes);

        Optional<SymbolTable> targetTable;
        if (isCurrentClass) {
            targetTable = Optional.of(table);
        } else {
            var fqName = table.getImportedFullyQualifiedName(className);
            targetTable = fqName.isPresent()
                    ? ((JmmSymbolTable) table).getImportedSymbolTable(fqName.get())
                    : ((JmmSymbolTable) table).getImplicitImport(className);
        }

        if (!isCurrentClass && targetTable.isPresent() && targetTable.get().getMethod(constructorSignature).isEmpty()) {
            var message = String.format(
                    "No constructor found for class '%s' with argument types %s.",
                    className, argTypes);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newClassExpr),
                    NodeUtils.getColumn(newClassExpr),
                    message,
                    null
            ));
        }

        return null;
    }

    private List<JmmType> getArgTypes(JmmNode callNode, SymbolTable table) {
        var argsNodes = callNode.getChildren(JmmKind.ARGS);
        if (argsNodes.isEmpty()) {
            return List.of();
        }

        return argsNodes.getFirst().getChildren().stream()
                .map(arg -> TypeUtils.with(table).getExprType(arg))
                .toList();
    }

    private boolean existsInCurrentClassOrSuperclass(Signature signature, SymbolTable table) {
        if (table.getMethod(signature).isPresent()) {
            return true;
        }

        if (!(table instanceof JmmSymbolTable jmmTable)) {
            return false;
        }

        var superName = table.getSuperFullyQualifiedName();
        if (superName == null) {
            return false;
        }

        var maybeSuperTable = jmmTable.getImportedSymbolTable(superName);

        if (maybeSuperTable.isEmpty()) {
            var simpleSuper = superName.substring(superName.lastIndexOf('.') + 1);
            maybeSuperTable = jmmTable.getImplicitImport(simpleSuper);
        }

        return maybeSuperTable.isPresent() && maybeSuperTable.get().getMethod(signature).isPresent();
    }

    private Optional<SymbolTable> resolveTableFromReceiverType(JmmType receiverType, SymbolTable table) {
        if (!(receiverType instanceof JmmClassType classType)) return Optional.empty();

        var fqName = classType.fullyQualifiedName();
        var simpleName = fqName.substring(fqName.lastIndexOf('.') + 1);

        // 1) current class
        if (simpleName.equals(table.getClassName()) || fqName.equals(table.getFullyQualifiedName())) {
            return Optional.of(table);
        }

        // 2) explicit import
        var imported = ((JmmSymbolTable) table).getImportedSymbolTable(fqName);
        if (imported.isPresent()) return imported;

        imported = ((JmmSymbolTable) table).getImportedSymbolTable(simpleName);
        if(imported.isPresent()) return imported;

        // 3) implicit import
        var implicit = ((JmmSymbolTable) table).getImplicitImport(simpleName);
        if (implicit.isPresent()) return implicit;

        return Optional.empty();
    }
}
