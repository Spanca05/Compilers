package pt.up.fe.comp2026.optimization.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.ArrayList;

import pt.up.fe.comp2026.jmm.ast.JmmKind;

class BranchEliminator {

    boolean run(JmmNode methodDecl) {
        boolean changed = false;
        for (var ifStmt : new ArrayList<>(methodDecl.getDescendants(JmmKind.IF_STMT))) {
            if (ifStmt.getParent() == null)
                continue;
            var cond = ifStmt.getChild(0);
            if (!JmmKind.BOOLEAN_LITERAL.equals(cond.getKind()))
                continue;
            boolean value = Boolean.parseBoolean(cond.get("value"));
            JmmNode replacement;
            if (value) {
                replacement = AstNodes.detach(ifStmt.getChild(1));
            } else if (ifStmt.getNumChildren() > 2) {
                replacement = AstNodes.detach(ifStmt.getChild(2));
            } else {
                replacement = new JmmNodeImpl(JmmKind.BLOCK_STMT);
            }
            AstNodes.replaceNode(ifStmt, replacement);
            changed = true;
        }
        return changed;
    }
}
