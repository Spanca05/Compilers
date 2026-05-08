package pt.up.fe.comp2026.optimization.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import pt.up.fe.comp2026.jmm.ast.JmmKind;

final class AstNodes {

    private AstNodes() {
    }

    static boolean isLiteral(JmmNode n) {
        return JmmKind.INTEGER_LITERAL.equals(n.getKind()) || JmmKind.BOOLEAN_LITERAL.equals(n.getKind());
    }

    static JmmNode cloneLiteral(JmmNode lit) {
        var copy = new JmmNodeImpl(lit.getKind());
        copy.put("value", lit.get("value"));
        return copy;
    }

    static JmmNode cloneSubtree(JmmNode node) {
        if (node.getParent() != null) {
            return deepCopy(node);
        }
        return node;
    }

    static JmmNode deepCopy(JmmNode node) {
        var copy = (JmmNodeImpl) node.copyShallow(node.getKind());
        for (var child : node.getChildren()) {
            copy.add(deepCopy(child));
        }
        return copy;
    }

    static JmmNode makeIntLiteral(int value) {
        var node = new JmmNodeImpl(JmmKind.INTEGER_LITERAL);
        node.put("value", String.valueOf(value));
        return node;
    }

    static JmmNode makeBoolLiteral(boolean value) {
        var node = new JmmNodeImpl(JmmKind.BOOLEAN_LITERAL);
        node.put("value", String.valueOf(value));
        return node;
    }

    static JmmNode detach(JmmNode node) {
        JmmNode parent = node.getParent();
        if (parent != null) {
            parent.removeChild(node);
        }
        return node;
    }

    static void replaceNode(JmmNode oldNode, JmmNode newNode) {
        JmmNode parent = oldNode.getParent();
        if (parent == null)
            return;
        int idx = oldNode.getIndexOfSelf();
        if (newNode.getParent() != null) {
            newNode.getParent().removeChild(newNode);
        }
        parent.removeChild(idx);
        parent.add(newNode, idx);
    }
}
