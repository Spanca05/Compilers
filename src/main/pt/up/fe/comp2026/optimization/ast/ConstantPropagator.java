package pt.up.fe.comp2026.optimization.ast;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.up.fe.comp2026.jmm.ast.JmmKind;

class ConstantPropagator {

    boolean run(JmmNode methodDecl, MethodSymbol method) {
        var stmts = methodDecl.getChildren(JmmKind.STMT);
        boolean[] changed = { false };
        Map<String, JmmNode> state = new HashMap<>();
        for (var stmt : stmts) {
            state = propagateInStmt(stmt, state, method, changed);
        }
        return changed[0];
    }

    private Map<String, JmmNode> propagateInStmt(JmmNode stmt, Map<String, JmmNode> state,
            MethodSymbol method, boolean[] changed) {
        var kind = stmt.getKind();
        if (JmmKind.BLOCK_STMT.equals(kind)) {
            for (var child : stmt.getChildren()) {
                state = propagateInStmt(child, state, method, changed);
            }
            return state;
        }
        if (JmmKind.ASSIGN_STMT.equals(kind)) {
            return propagateInAssign(stmt, state, method, changed);
        }
        if (JmmKind.RETURN_STMT.equals(kind)) {
            if (stmt.getNumChildren() > 0) {
                substituteInExpr(stmt.getChild(0), state, method, changed);
            }
            return state;
        }
        if (JmmKind.EXPR_STMT.equals(kind)) {
            substituteInExpr(stmt.getChild(0), state, method, changed);
            return state;
        }
        if (JmmKind.IF_STMT.equals(kind)) {
            substituteInExpr(stmt.getChild(0), state, method, changed);
            var thenState = propagateInStmt(stmt.getChild(1), copy(state), method, changed);
            Map<String, JmmNode> elseState;
            if (stmt.getNumChildren() > 2) {
                elseState = propagateInStmt(stmt.getChild(2), copy(state), method, changed);
            } else {
                elseState = copy(state);
            }
            return merge(thenState, elseState);
        }
        if (JmmKind.WHILE_STMT.equals(kind) || JmmKind.FOR_STMT.equals(kind) || JmmKind.DO_WHILE_STMT.equals(kind)) {
            var killed = collectAssignedVars(stmt);
            for (var v : killed)
                state.remove(v);
            for (var child : stmt.getChildren()) {
                substituteInExpr(child, state, method, changed);
            }
            return state;
        }
        return state;
    }

    private Map<String, JmmNode> propagateInAssign(JmmNode stmt, Map<String, JmmNode> state,
            MethodSymbol method, boolean[] changed) {
        String varName = stmt.get(JmmAttributes.ASSIGN_STMT.VAR);
        boolean arrayStore = stmt.getNumChildren() > 1;
        if (arrayStore) {
            for (var child : stmt.getChildren()) {
                substituteInExpr(child, state, method, changed);
            }
            return state;
        }

        var rhs = stmt.getChild(0);
        substituteInExpr(rhs, state, method, changed);

        boolean isLocal = method.getLocalVariable(varName).isPresent()
                && method.getParameter(varName).isEmpty();
        if (!isLocal) {
            state.remove(varName);
            return state;
        }
        var newRhs = stmt.getChild(0);
        if (AstNodes.isLiteral(newRhs)) {
            state.put(varName, newRhs);
        } else {
            state.remove(varName);
        }
        return state;
    }

    private void substituteInExpr(JmmNode expr, Map<String, JmmNode> state,
            MethodSymbol method, boolean[] changed) {
        for (var child : new ArrayList<>(expr.getChildren())) {
            substituteInExpr(child, state, method, changed);
        }
        if (JmmKind.VAR_REF_EXPR.equals(expr.getKind())) {
            String name = expr.get("name");
            JmmNode lit = state.get(name);
            if (lit == null)
                return;
            if (method.getLocalVariable(name).isEmpty() && method.getParameter(name).isEmpty()) {
                return;
            }
            AstNodes.replaceNode(expr, AstNodes.cloneLiteral(lit));
            changed[0] = true;
        }
    }

    private List<String> collectAssignedVars(JmmNode root) {
        var list = new ArrayList<String>();
        collectAssignedVarsHelper(root, list);
        return list;
    }

    private void collectAssignedVarsHelper(JmmNode node, List<String> out) {
        if (JmmKind.ASSIGN_STMT.equals(node.getKind())) {
            out.add(node.get(JmmAttributes.ASSIGN_STMT.VAR));
        }
        for (var c : node.getChildren())
            collectAssignedVarsHelper(c, out);
    }

    private Map<String, JmmNode> copy(Map<String, JmmNode> state) {
        return new HashMap<>(state);
    }

    private Map<String, JmmNode> merge(Map<String, JmmNode> a, Map<String, JmmNode> b) {
        Map<String, JmmNode> out = new HashMap<>();
        for (var e : a.entrySet()) {
            JmmNode bv = b.get(e.getKey());
            if (bv != null && literalEquals(e.getValue(), bv)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private boolean literalEquals(JmmNode a, JmmNode b) {
        if (!a.getKind().equals(b.getKind()))
            return false;
        return a.get("value").equals(b.get("value"));
    }
}
