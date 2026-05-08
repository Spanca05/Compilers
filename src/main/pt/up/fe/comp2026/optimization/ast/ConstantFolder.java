package pt.up.fe.comp2026.optimization.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;

import pt.up.fe.comp2026.jmm.ast.JmmKind;

class ConstantFolder {

    boolean run(JmmNode methodDecl) {
        boolean changed = false;
        for (var node : new ArrayList<>(methodDecl.getDescendants())) {
            if (node.getParent() == null)
                continue;
            JmmNode replacement = tryFold(node);
            if (replacement != null) {
                AstNodes.replaceNode(node, replacement);
                changed = true;
            }
        }
        return changed;
    }

    private JmmNode tryFold(JmmNode node) {
        var kind = node.getKind();
        if (JmmKind.PARENTESES_EXPR.equals(kind)) {
            var inner = node.getChild(0);
            if (AstNodes.isLiteral(inner)) {
                return AstNodes.cloneLiteral(inner);
            }
            return null;
        }
        if (JmmKind.NEG_EXPR.equals(kind)) {
            var inner = node.getChild(0);
            if (JmmKind.BOOLEAN_LITERAL.equals(inner.getKind())) {
                return AstNodes.makeBoolLiteral(!Boolean.parseBoolean(inner.get("value")));
            }
            return null;
        }
        if (JmmKind.UNARY_EXPR.equals(kind)) {
            var inner = node.getChild(0);
            if (JmmKind.INTEGER_LITERAL.equals(inner.getKind())) {
                String op = node.get("op");
                int v = Integer.parseInt(inner.get("value"));
                if (op.equals("-"))
                    return AstNodes.makeIntLiteral(-v);
                if (op.equals("+"))
                    return AstNodes.makeIntLiteral(v);
            }
            return null;
        }
        if (JmmKind.BINARY_EXPR.equals(kind)) {
            return tryFoldBinary(node);
        }
        return null;
    }

    private JmmNode tryFoldBinary(JmmNode node) {
        var op = node.get("op");
        var left = node.getChild(0);
        var right = node.getChild(1);
        boolean leftBool = JmmKind.BOOLEAN_LITERAL.equals(left.getKind());
        boolean rightBool = JmmKind.BOOLEAN_LITERAL.equals(right.getKind());
        boolean leftInt = JmmKind.INTEGER_LITERAL.equals(left.getKind());
        boolean rightInt = JmmKind.INTEGER_LITERAL.equals(right.getKind());

        if (op.equals("&&")) {
            if (leftBool) {
                boolean lv = Boolean.parseBoolean(left.get("value"));
                if (!lv)
                    return AstNodes.makeBoolLiteral(false);
                return AstNodes.cloneSubtree(right);
            }
            if (rightBool) {
                boolean rv = Boolean.parseBoolean(right.get("value"));
                if (rv)
                    return AstNodes.cloneSubtree(left);
            }
            return null;
        }
        if (op.equals("||")) {
            if (leftBool) {
                boolean lv = Boolean.parseBoolean(left.get("value"));
                if (lv)
                    return AstNodes.makeBoolLiteral(true);
                return AstNodes.cloneSubtree(right);
            }
            if (rightBool) {
                boolean rv = Boolean.parseBoolean(right.get("value"));
                if (!rv)
                    return AstNodes.cloneSubtree(left);
            }
            return null;
        }

        if (leftInt && rightInt) {
            int l = Integer.parseInt(left.get("value"));
            int r = Integer.parseInt(right.get("value"));
            switch (op) {
                case "+":
                    return AstNodes.makeIntLiteral(l + r);
                case "-":
                    return AstNodes.makeIntLiteral(l - r);
                case "*":
                    return AstNodes.makeIntLiteral(l * r);
                case "/":
                    if (r == 0)
                        return null;
                    return AstNodes.makeIntLiteral(l / r);
                case "%":
                    if (r == 0)
                        return null;
                    return AstNodes.makeIntLiteral(l % r);
                case "<":
                    return AstNodes.makeBoolLiteral(l < r);
                case ">":
                    return AstNodes.makeBoolLiteral(l > r);
                case "<=":
                    return AstNodes.makeBoolLiteral(l <= r);
                case ">=":
                    return AstNodes.makeBoolLiteral(l >= r);
                case "==":
                    return AstNodes.makeBoolLiteral(l == r);
                case "!=":
                    return AstNodes.makeBoolLiteral(l != r);
            }
        }
        if (leftBool && rightBool) {
            boolean l = Boolean.parseBoolean(left.get("value"));
            boolean r = Boolean.parseBoolean(right.get("value"));
            switch (op) {
                case "==":
                    return AstNodes.makeBoolLiteral(l == r);
                case "!=":
                    return AstNodes.makeBoolLiteral(l != r);
            }
        }
        return null;
    }
}
