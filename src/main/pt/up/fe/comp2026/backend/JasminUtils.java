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
        fullClassnames.put("this", ollirResult.getOllirClass().getClassName());
        // This will be get caught as STRING OLLIR element type.
        // And classes cannot be named String, since it is an OLLIR reserved keyword
        //imports.put("String", "java/lang/String");

        for (var fullImport : ollirResult.getOllirClass().getImports()) {
            var splitted = fullImport.split("\\.");

            // Last element will be the key
            var key = splitted[splitted.length - 1];
            fullClassnames.put(key, fullImport.replace('.', '/'));
        }
    }



    public String getTypePrefix(Type type) {
        System.out.println("[TODO] JasminUtils.getTypePrefix(): Assumes it is always int, needs to be expanded");
        if (type instanceof BuiltinType b) {
            return switch (b.getKind()) {
                case INT32, BOOLEAN -> "i";
                case VOID -> "";
                case STRING -> "a";
            };
        }
        return "a";
    }

    public String getTypeDescriptor(Type type) {

        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case VOID -> "V";
                case STRING -> "Ljava/lang/String;";tes
            };
        }
        if (type instanceof ArrayType arrayType) {
            return "[".repeat(arrayType.getNumDimensions()) + getTypeDescriptor(arrayType.getElementType());
        }
        if (type instanceof ClassType classType) {
            var fullName = fullClassnames.getOrDefault(classType.getName(), "java/lang/" + classType.getName());
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
