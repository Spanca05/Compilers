package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    private final Map<String, String> fullClassnames;

    private final Importer importer;

    public JasminUtils(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        this.importer = Importer.fromThisClassPath();
        // Build imports table
        fullClassnames = new HashMap<>();

        // Predefined classnames
        var selfFqn = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        fullClassnames.put("this", selfFqn);
        fullClassnames.put(ollirResult.getOllirClass().getClassName(), selfFqn);
        fullClassnames.put("String", "java/lang/String");
        fullClassnames.put("Object", "java/lang/Object");

        for (var fullImport : ollirResult.getOllirClass().getImports()) {
            var splitted = fullImport.split("\\.");

            // Last element will be the key
            var key = splitted[splitted.length - 1];
            fullClassnames.put(key, fullImport.replace('.', '/'));
        }

        // Resolve super class (may not be in imports if it's java.lang.*)
        var superClass = ollirResult.getOllirClass().getSuperClass();
        if (superClass != null && !superClass.isEmpty() && !fullClassnames.containsKey(superClass)) {
            // Default to java/lang/<superClass> as fallback (covers Exception, etc.)
            fullClassnames.put(superClass, "java/lang/" + superClass);
        }
    }

    public String resolveClassName(String name) {
        if (name.contains(".")) {
            return name.replace('.', '/');
        }
        return fullClassnames.getOrDefault(name, name);
    }



    public String getTypePrefix(Type type) {
        if (type instanceof BuiltinType b) {
            return switch (b.getKind()) {
                case INT32, BOOLEAN -> "i";
                case VOID -> "";
                case STRING -> "a";
            };
        }
        return "a";
    }

    public String getFullClassName(Type type) {
        if (type instanceof ClassType classType) {
            return fullClassnames.getOrDefault(classType.getName(), classType.getName());
        }
        throw new RuntimeException("Expected class type, got: " + type);
    }

    public String getTypeDescriptor(Type type) {

        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case VOID -> "V";
                default -> "";
            };
        }
        if (type instanceof ArrayType arrayType) {
            return "[".repeat(arrayType.getNumDimensions()) + getTypeDescriptor(arrayType.getElementType());
        }
        if (type instanceof ClassType classType) {
            var fullName = "";
            if (fullClassnames.containsKey(classType.getName())) {
                fullName = fullClassnames.get(classType.getName());
            }
            else if (classType.getName().equals("Object")) {
                fullName = "java/lang/Object";
            }
            else if (classType.getName().equals("String")) {
                fullName = "java/lang/String";
            }
            else {
                fullName = classType.getName();
            }
            return "L" + fullName + ";";
        }
        throw new RuntimeException("Unknown type: '" + type + "'");
    }




    public String getModifier(AccessModifier accessModifier) {
        return switch (accessModifier) {
            case DEFAULT -> "";
            case PUBLIC -> "public ";
            case PRIVATE -> "private ";
            case PROTECTED -> "protected ";
            default -> accessModifier.name().toLowerCase() + " ";
        };
    }

    public String getLoad(Descriptor reg) {
        var prefix = getTypePrefix(reg.getVarType());
        var value = reg.getVirtualReg();

        return prefix + "load " + value;
    }

    public String getStore(Descriptor reg) {
        var prefix = getTypePrefix(reg.getVarType());
        var value = reg.getVirtualReg();

        return prefix + "store " + value;
    }

}
