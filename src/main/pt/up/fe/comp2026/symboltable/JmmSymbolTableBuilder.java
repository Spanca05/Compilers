package pt.up.fe.comp2026.symboltable;

import java_cup.runtime.symbol;
import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Visibility;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.Kind;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.utils.Attributes;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

public class JmmSymbolTableBuilder {

    private final JmmNode root;
    private final Importer importer;
    public String className;
    public String superClassName;
    private final List<Report> reports;
    private final List<String> imports;
    private final Map<String, String> declaredClasses;

    private Map<String, String> nameToPath = new HashMap<>();

    /**
     * Only build() can create new instances, this ensures that each instance is used only once,
     * and we do not have to worry about "cleaning state".
     */
    private JmmSymbolTableBuilder(JmmNode root) {
        this.root = root;
        reports = new ArrayList<>();
        imports = new ArrayList<>();
        declaredClasses = new HashMap<>();
        this.importer = Importer.fromThisClassPath();
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null);
    }

    public static SymbolTableBuilderResult build(JmmNode root) {
        return new JmmSymbolTableBuilder(root).buildInternal();
    }

    private SymbolTableBuilderResult buildInternal() {


        var packageDecl = root.getChildren(PACKAGE_DECL).getFirst();
        var packagePathList = packageDecl.getObjectAsList("path", String.class);
        var packagePath = String.join(".", packagePathList);

        var classDecl = root.getObject("classNode", JmmNode.class);
        SpecsCheck.checkArgument(CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);

        this.className = classDecl.get("name");
        this.superClassName = classDecl.hasAttribute("superclass") ? classDecl.get("superclass") : null;
        var fullyQualifiedName = packagePath + "." + className;
        // Check if className is available
        if (declaredClasses.containsKey(className)) {
            reports.add(newError(root, "'" + className + "' is already defined in this compilation unit"));
        }
        declaredClasses.put(className, fullyQualifiedName);

        nameToPath = new HashMap<String, String>();
        var importDecls = root.getChildren(IMPORT_DECL);

        for (var importDecl: importDecls) {
            var importPathList = new ArrayList<>(importDecl.getObjectAsList("path", String.class));
            var fullImport = String.join(".", importPathList);

            if (imports.contains(fullImport)) {
                continue;
            }
            
            var importName = importPathList.getLast();
            importPathList.removeLast();
            var importPath = String.join(".", importPathList);

            if (nameToPath.containsKey(importName) &&
                    !nameToPath.get(importName).equals(importPath)) {
                reports.add(newError(importDecl, "'"+importPath+"."+importName+"' is already defined"));
                continue;
            }
            
            nameToPath.put(importName, importPath);
            imports.add(fullImport);
        }
        // get superclass fully qualified name
        var superFullyQualified = superClassName != null
                ? nameToPath.getOrDefault(superClassName, "") + "." + superClassName
                : null;

        if (superClassName != null && !nameToPath.containsKey(superClassName) && !superClassName.equals(className)) {
            reports.add(newError(classDecl, "Class '" + superClassName + "' is not imported."));
        }

        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var symbolTable = new JmmSymbolTable(imports, fullyQualifiedName, superFullyQualified, fields, methods, importer);
        return new SymbolTableBuilderResult(symbolTable, reports);
    }

    private List<MethodSymbol> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(this::buildMethod)
                .toList();

    }

    private MethodSymbol buildMethod(JmmNode method) {
        var methodName = method.get("name");

        // System.out.println("[TODO] JmmSymbolTableBuilder.buildMethod(): Assuming return type of method is always int, and always has a single int parameter, needs to be expanded");
        var returnTypeNode = method.getChild(0);
        var returnType = buildType(returnTypeNode);

        var params = method.getChildren(PARAM).stream()
                .map(param -> {
                    var typeNode = param.getObject(JmmAttributes.PARAM.TYPE_NODE, JmmNode.class);
                    var type = buildType((typeNode));
                    return new Symbol(type, param.get(JmmAttributes.PARAM.NAME));
                }).toList();

        // System.out.println("[TODO] JmmSymbolTableBuilder.buildMethod(): Assuming all VarDecls are ints, needs to be expanded");
        var locals = method.getChildren(VAR_DECL).stream()
                .map(this::buildField
                ).toList();

        var visibility =  Visibility.PUBLIC;
        var isStatic = method.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
        return new MethodSymbol(methodName, returnType, params, locals, isStatic, visibility);
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        return classDecl.getChildren(VAR_DECL).stream()
                .map(this::buildField)
                .toList();
    }

    private Symbol buildField(JmmNode field) {
        var fieldName = field.get(JmmAttributes.VAR_DECL.NAME);
        var typeNode = field.getObject(JmmAttributes.VAR_DECL.TYPE_NODE, JmmNode.class);
        var fieldType = buildType(typeNode);

        return new Symbol(fieldType, fieldName);
    }

    private JmmType buildType(JmmNode typeNode) {
        // Caso base: tipo simples
        if (typeNode.getAttributes().contains("name")) {
            var typeName = typeNode.get("name");
            var primitiveType = JmmPrimitiveType.fromString(typeName);

            if (primitiveType.isPresent()) {
                return primitiveType.get();
            }
            if (typeName.equals(className))
                return new JmmClassType(declaredClasses.get(className),false,false);

            var isImported = nameToPath.containsKey(typeName);
            var resolvedName = isImported ? nameToPath.get(typeName) + "." + typeName : typeName;

            System.out.println(resolvedName);
            // Classe (Assumimos não importada e não static por agora)
            return new JmmClassType(resolvedName, isImported, false);
        }

        // Caso array
        var innerTypeNode = typeNode.getChildren(TYPE).getFirst();
        var innerType = buildType(innerTypeNode);

        if (innerType instanceof JmmArrayType arrayType) {
            return new JmmArrayType(
                    arrayType.itemType(),
                    arrayType.dimension() + 1
            );
        }

        return new JmmArrayType(innerType, 1);
    }


}
