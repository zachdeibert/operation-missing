package com.github.zachdeibert.operationmanipulation.controller;

import android.util.Log;

import com.github.zachdeibert.operationmanipulation.model.BinaryOperator;
import com.github.zachdeibert.operationmanipulation.model.Equation;
import com.github.zachdeibert.operationmanipulation.model.ExpressionItem;
import com.github.zachdeibert.operationmanipulation.model.GroupingOperator;
import com.github.zachdeibert.operationmanipulation.model.Level;
import com.github.zachdeibert.operationmanipulation.model.Operand;
import com.github.zachdeibert.operationmanipulation.model.Operator;
import com.github.zachdeibert.operationmanipulation.model.OperatorType;
import com.github.zachdeibert.operationmanipulation.model.UnaryOperator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

public class EquationGenerator extends Thread {
    private static final int QUEUE_SIZE = 3;
    private final Random random;
    private int operands;
    private OperatorType[] operators;
    private Level level;
    private final Queue<Equation> generatedEquations;

    private boolean solve(Equation eq, BinaryOperator[] binaryOperators, UnaryOperator[] unaryOperators, boolean[] negate) {
        if (eq.getOperandCount() == negate.length) {
            int numUnary = 0;
            for (UnaryOperator op : unaryOperators) {
                if (op != null) {
                    ++numUnary;
                }
            }
            Map<Integer, List<GroupingOperator>> operatorTypes = new HashMap<>();
            for (OperatorType op : getOperators()) {
                if (op.getOperator() instanceof GroupingOperator) {
                    int type = ((GroupingOperator) op.getOperator()).getType();
                    List<GroupingOperator> list;
                    if (operatorTypes.containsKey(type)) {
                        list = operatorTypes.get(type);
                    } else {
                        list = new ArrayList<>();
                        operatorTypes.put(type, list);
                    }
                    list.add((GroupingOperator) op.getOperator());
                }
            }
            if (operatorTypes.isEmpty()) {
                eq.clear();
                int i = 0;
                for (ExpressionItem item : eq.getLeftSide()) {
                    if (item instanceof Operand) {
                        ExpressionItem op = item;
                        if (unaryOperators[i] != null) {
                            unaryOperators[i] = (UnaryOperator) unaryOperators[i].clone();
                            eq.insertOperatorAfter(op, unaryOperators[i]);
                            op = unaryOperators[i];
                        }
                        eq.insertOperatorAfter(op, binaryOperators[i++]);
                        if (i >= binaryOperators.length) {
                            break;
                        }
                    }
                }
                if (EquationSolver.isCorrect(eq)) {
                    Log.i("EquationGenerator", eq.toString());
                    return true;
                }
            }
            for (int type : operatorTypes.keySet()) {
                List<GroupingOperator> list = operatorTypes.get(type);
                for (int start = 0; start < 2 * eq.getOperandCount() + numUnary - 1; ++start) {
                    for (int end = start + type == 0 ? 3 : 2; end < 2 * eq.getOperandCount() + numUnary; ++end) {
                        for (GroupingOperator startOp : list) {
                            if (startOp.getLevel() >= 0) {
                                for (GroupingOperator endOp : list) {
                                    if (endOp.getLevel() <= 0) {
                                        eq.clear();
                                        int i = 0;
                                        for (ExpressionItem item : eq.getLeftSide()) {
                                            if (item instanceof Operand) {
                                                ExpressionItem op = item;
                                                if (unaryOperators[i] != null) {
                                                    unaryOperators[i] = (UnaryOperator) unaryOperators[i].clone();
                                                    eq.insertOperatorAfter(op, unaryOperators[i]);
                                                    op = unaryOperators[i];
                                                }
                                                eq.insertOperatorAfter(op, binaryOperators[i++]);
                                                if (i >= binaryOperators.length) {
                                                    break;
                                                }
                                            }
                                        }
                                        eq.insertOperatorAt(start, startOp);
                                        eq.insertOperatorAt(end, endOp);
                                        i = 0;
                                        for (ExpressionItem item : eq.getLeftSide()) {
                                            if (item instanceof Operand) {
                                                if (negate[i++]) {
                                                    eq.insertOperatorBefore(item, Operator.SUBTRACTION);
                                                }
                                            }
                                        }
                                        if (EquationSolver.isComplete(eq) && EquationSolver.isCorrect(eq)) {
                                            Log.i("EquationGenerator", eq.toString());
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } else {
            boolean[] subs = new boolean[negate.length + 1];
            System.arraycopy(negate, 0, subs, 0, negate.length);
            subs[negate.length] = false;
            if (solve(eq, binaryOperators, unaryOperators, subs)) {
                return true;
            }
            subs[negate.length] = true;
            return solve(eq, binaryOperators, unaryOperators, subs);
        }
    }

    private boolean solve(Equation eq, BinaryOperator[] binaryOperators, UnaryOperator[] unaryOperators) {
        if (eq.getOperandCount() == binaryOperators.length + 1) {
            return solve(eq, binaryOperators, unaryOperators, new boolean[0]);
        } else {
            BinaryOperator[] subs = new BinaryOperator[binaryOperators.length + 1];
            System.arraycopy(binaryOperators, 0, subs, 0, binaryOperators.length);
            for (OperatorType op : getOperators()) {
                if (op.getOperator() instanceof BinaryOperator) {
                    subs[binaryOperators.length] = (BinaryOperator) op.getOperator();
                    if (solve(eq, subs, unaryOperators)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private boolean solve(Equation eq, UnaryOperator[] unaryOperators) {
        if (eq.getOperandCount() == unaryOperators.length) {
            return solve(eq, new BinaryOperator[0], unaryOperators);
        } else {
            UnaryOperator[] subs = new UnaryOperator[unaryOperators.length + 1];
            System.arraycopy(unaryOperators, 0, subs, 0, unaryOperators.length);
            subs[unaryOperators.length] = null;
            if (solve(eq, subs)) {
                return true;
            }
            for (OperatorType op : getOperators()) {
                if (op.getOperator() instanceof UnaryOperator) {
                    subs[unaryOperators.length] = (UnaryOperator) op.getOperator();
                    if (solve(eq, subs)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean solve(Equation eq) {
        try {
            return solve(eq, new UnaryOperator[0]);
        } finally {
            eq.clear();
        }
    }

    public Equation generate() {
        Operand[] operands = new Operand[getOperands()];
        int result;
        while (true) {
            for (int i = 0; i < operands.length; ++i) {
                operands[i] = new Operand(random.nextInt(10));
            }
            result = random.nextInt(10);
            Equation eq = new Equation(getLevel(), new Operand(result), operands);
            if (solve(eq)) {
                return eq;
            }
        }
    }

    public Equation getEquation() {
        if (generatedEquations.size() > 0) {
            return generatedEquations.poll();
        } else {
            return generate();
        }
    }

    @Override
    public void run() {
        while (true) {
            Equation eq = null;
            try {
                if (generatedEquations.size() < QUEUE_SIZE) {
                    eq = generate();
                }
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                continue;
            }
            if (eq != null) {
                generatedEquations.offer(eq);
            }
        }
    }

    public int getOperands() {
        return operands;
    }

    public OperatorType[] getOperators() {
        return operators;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
        this.operands = level.getNumberOfOperands();
        this.operators = level.getAllowedOperators();
        interrupt();
        generatedEquations.clear();
    }

    public EquationGenerator() {
        random = new Random();
        generatedEquations = new ArrayBlockingQueue<>(QUEUE_SIZE);
        setLevel(Level.TwoAddition);
    }
}
