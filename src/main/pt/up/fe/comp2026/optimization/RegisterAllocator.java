package pt.up.fe.comp2026.optimization;

import org.specs.comp.ollir.ArrayOperand;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import org.specs.comp.ollir.VarScope;
import org.specs.comp.ollir.inst.AssignInstruction;
import org.specs.comp.ollir.inst.BinaryOpInstruction;
import org.specs.comp.ollir.inst.CallInstruction;
import org.specs.comp.ollir.inst.CondBranchInstruction;
import org.specs.comp.ollir.inst.GetFieldInstruction;
import org.specs.comp.ollir.inst.Instruction;
import org.specs.comp.ollir.inst.PutFieldInstruction;
import org.specs.comp.ollir.inst.ReturnInstruction;
import org.specs.comp.ollir.inst.SingleOpInstruction;
import org.specs.comp.ollir.inst.UnaryOpInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegisterAllocator {

    public static void allocate(ClassUnit ollirClass, int registerAllocation) {
        if (registerAllocation < 0)
            return;
        ollirClass.buildCFGs();
        for (var method : ollirClass.getMethods()) {
            if (method.isConstructMethod())
                continue;
            allocateMethod(method, registerAllocation);
        }
    }

    private static void allocateMethod(Method method, int registerAllocation) {
        var varTable = method.getVarTable();
        List<Instruction> instructions = method.getInstructions();

        Map<Instruction, Set<String>> use = new HashMap<>();
        Map<Instruction, Set<String>> def = new HashMap<>();
        for (var inst : instructions) {
            use.put(inst, computeUse(inst));
            def.put(inst, computeDef(inst));
        }

        Map<Instruction, Set<String>> liveIn = new HashMap<>();
        Map<Instruction, Set<String>> liveOut = new HashMap<>();
        for (var inst : instructions) {
            liveIn.put(inst, new HashSet<>());
            liveOut.put(inst, new HashSet<>());
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (var inst : instructions) {
                Set<String> newOut = new HashSet<>();
                for (var successor : inst.getSuccessorsAsInst()) {
                    newOut.addAll(liveIn.get(successor));
                }
                Set<String> newIn = new HashSet<>(newOut);
                newIn.removeAll(def.get(inst));
                newIn.addAll(use.get(inst));
                if (!newOut.equals(liveOut.get(inst)) || !newIn.equals(liveIn.get(inst))) {
                    changed = true;
                    liveOut.put(inst, newOut);
                    liveIn.put(inst, newIn);
                }
            }
        }

        Set<String> localVars = new HashSet<>();
        for (var entry : varTable.entrySet()) {
            String name = entry.getKey();
            Descriptor d = entry.getValue();
            if (d.getScope() != VarScope.LOCAL)
                continue;
            if (name.equalsIgnoreCase("this"))
                continue;
            localVars.add(name);
        }

        Map<String, Set<String>> graph = new HashMap<>();
        for (var v : localVars)
            graph.put(v, new HashSet<>());

        for (var inst : instructions) {
            Set<String> defs = def.get(inst);
            Set<String> live = liveOut.get(inst);
            for (var d : defs) {
                if (!localVars.contains(d))
                    continue;
                for (var l : live) {
                    if (l.equals(d) || !localVars.contains(l))
                        continue;
                    graph.get(d).add(l);
                    graph.get(l).add(d);
                }
            }
            Set<String> liveAtPoint = new HashSet<>(liveIn.get(inst));
            for (var a : liveAtPoint) {
                if (!localVars.contains(a))
                    continue;
                for (var b : liveAtPoint) {
                    if (a.equals(b) || !localVars.contains(b))
                        continue;
                    graph.get(a).add(b);
                }
            }
        }

        int firstReg = (method.isStaticMethod() ? 0 : 1) + method.getParams().size();
        Map<String, Integer> colors = colorGreedy(graph, localVars, firstReg, registerAllocation);

        for (var v : localVars) {
            Integer reg = colors.get(v);
            if (reg != null) {
                varTable.get(v).setVirtualReg(reg);
            }
        }
    }

    private static Map<String, Integer> colorGreedy(Map<String, Set<String>> graph, Set<String> nodes,
            int firstReg, int budget) {
        var ordered = new ArrayList<>(nodes);
        ordered.sort((a, b) -> Integer.compare(graph.get(b).size(), graph.get(a).size()));
        int maxAllowed = budget == 0 ? Integer.MAX_VALUE : firstReg + budget;

        Map<String, Integer> colors = new HashMap<>();
        for (var v : ordered) {
            Set<Integer> used = new HashSet<>();
            for (var n : graph.get(v)) {
                if (colors.containsKey(n))
                    used.add(colors.get(n));
            }
            for (int c = firstReg; c < maxAllowed; c++) {
                if (!used.contains(c)) {
                    colors.put(v, c);
                    break;
                }
            }
        }
        return colors;
    }

    private static Set<String> computeDef(Instruction inst) {
        if (inst instanceof AssignInstruction assign) {
            Element dest = assign.getDest();
            if (dest instanceof Operand op && !(dest instanceof ArrayOperand) && !op.isLiteral()) {
                return Set.of(op.getName());
            }
        }
        return Set.of();
    }

    private static Set<String> computeUse(Instruction inst) {
        Set<String> out = new HashSet<>();
        collectUses(inst, out);
        return out;
    }

    private static void collectUses(Object node, Set<String> out) {
        if (node == null)
            return;
        if (node instanceof AssignInstruction assign) {
            Element dest = assign.getDest();
            if (dest instanceof ArrayOperand ao) {
                addOperand(ao.getName(), out);
                for (var idx : ao.getIndexOperands())
                    collectUses(idx, out);
            }
            collectUses(assign.getRhs(), out);
        } else if (node instanceof BinaryOpInstruction bo) {
            collectUses(bo.getLeftOperand(), out);
            collectUses(bo.getRightOperand(), out);
        } else if (node instanceof UnaryOpInstruction uo) {
            collectUses(uo.getOperand(), out);
        } else if (node instanceof SingleOpInstruction so) {
            collectUses(so.getSingleOperand(), out);
        } else if (node instanceof CallInstruction call) {
            collectUses(call.getCaller(), out);
            for (var arg : call.getArguments())
                collectUses(arg, out);
        } else if (node instanceof ReturnInstruction ret) {
            ret.getOperand().ifPresent(op -> collectUses(op, out));
        } else if (node instanceof CondBranchInstruction cb) {
            for (var op : cb.getOperands())
                collectUses(op, out);
        } else if (node instanceof PutFieldInstruction pf) {
            for (var op : pf.getOperands())
                collectUses(op, out);
        } else if (node instanceof GetFieldInstruction gf) {
            collectUses(gf.getObject(), out);
        } else if (node instanceof ArrayOperand ao) {
            addOperand(ao.getName(), out);
            for (var idx : ao.getIndexOperands())
                collectUses(idx, out);
        } else if (node instanceof Operand op) {
            if (!op.isLiteral()) {
                addOperand(op.getName(), out);
            }
        }
    }

    private static void addOperand(String name, Set<String> out) {
        if (name == null)
            return;
        out.add(name);
    }
}
