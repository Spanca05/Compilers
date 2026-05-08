package pt.up.fe.comp2026.optimization.ast;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;

import pt.up.fe.comp2026.jmm.ast.JmmKind;

class DeadCodeEliminator {

    boolean run(JmmNode methodDecl, MethodSymbol method) {
        boolean changed = false;
        var assigns = methodDecl.getDescendants(JmmKind.ASSIGN_STMT);
        for (int i = assigns.size() - 1; i >= 0; i--) {
            var stmt = assigns.get(i);
            if (stmt.getParent() == null)
                continue;
            if (!isDeadAssignment(stmt, method))
                continue;
            stmt.getParent().removeChild(stmt);
            changed = true;
        }
        return changed;
    }

    private boolean isDeadAssignment(JmmNode assign, MethodSymbol method) {
        String varName = assign.get(JmmAttributes.ASSIGN_STMT.VAR);
        if (method.getLocalVariable(varName).isEmpty())
            return false;
        if (method.getParameter(varName).isPresent())
            return false;
        if (hasObservableSideEffect(assign, method))
            return false;
        return !varUsedAfter(assign, varName);
    }

    private boolean hasObservableSideEffect(JmmNode assign, MethodSymbol method) {
        for (var child : assign.getChildren()) {
            if (containsSideEffect(child, method))
                return true;
        }
        return false;
    }

    private boolean containsSideEffect(JmmNode node, MethodSymbol method) {
        var kind = node.getKind();
        if (JmmKind.METHOD_CALL_EXPR.equals(kind) || JmmKind.FUNCTION_CALL.equals(kind)
                || JmmKind.NEW_CLASS_EXPR.equals(kind)) {
            return true;
        }
        for (var c : node.getChildren()) {
            if (containsSideEffect(c, method))
                return true;
        }
        return false;
    }

    private boolean varUsedAfter(JmmNode stmt, String varName) {
        JmmNode current = stmt;
        while (current != null) {
            JmmNode parent = current.getParent();
            if (parent == null)
                return false;
            int idx = current.getIndexOfSelf();
            var siblings = parent.getChildren();
            for (int i = idx + 1; i < siblings.size(); i++) {
                if (containsRefTo(siblings.get(i), varName))
                    return true;
            }
            if (JmmKind.METHOD_DECL.equals(parent.getKind()))
                return false;
            current = parent;
        }
        return false;
    }

    private boolean containsRefTo(JmmNode node, String varName) {
        if (JmmKind.VAR_REF_EXPR.equals(node.getKind()) && node.get("name").equals(varName)) {
            return true;
        }
        for (var c : node.getChildren()) {
            if (containsRefTo(c, varName))
                return true;
        }
        return false;
    }
}
