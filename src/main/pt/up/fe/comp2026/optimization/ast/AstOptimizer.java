package pt.up.fe.comp2026.optimization.ast;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;

import java.util.Optional;

import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class AstOptimizer {

    private final SymbolTable table;
    private final ConstantFolder folder;
    private final ConstantPropagator propagator;
    private final BranchEliminator brancher;
    private final DeadCodeEliminator dce;

    public AstOptimizer(SymbolTable table) {
        this.table = table;
        this.folder = new ConstantFolder();
        this.propagator = new ConstantPropagator();
        this.brancher = new BranchEliminator();
        this.dce = new DeadCodeEliminator();
    }

    public void optimize(JmmNode root) {
        boolean changed = true;
        int safety = 10000;
        while (changed && safety-- > 0) {
            changed = false;
            for (var classDecl : root.getDescendants(JmmKind.CLASS_DECL)) {
                for (var method : classDecl.getChildren(JmmKind.METHOD_DECL)) {
                    var current = currentMethod(method);
                    if (current == null)
                        continue;

                    changed |= folder.run(method)
                            | propagator.run(method, current)
                            | brancher.run(method)
                            | dce.run(method, current);
                }
            }
        }
    }

    private MethodSymbol currentMethod(JmmNode methodDecl) {
        Signature signature = TypeUtils.with(table).getMethodDeclSignature(methodDecl);
        Optional<MethodSymbol> ms = table.getMethod(signature);
        return ms.orElse(null);
    }
}
