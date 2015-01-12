/*
 Copyright (c) 2007-2009 Kristofer Karlsson <kristofer.karlsson@gmail.com>
 Heavily modified by Colby Skeggs. Changes are Copyright (c) 2014 Colby Skeggs.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */
package se.krka.kahlua.vm;

import java.io.IOException;
import java.util.Random;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.stdlib.BaseLib;
import se.krka.kahlua.stdlib.CoroutineLib;
import se.krka.kahlua.stdlib.MathLib;
import se.krka.kahlua.stdlib.StringLib;
import se.krka.kahlua.stdlib.TableLib;

public class LuaState {

    public static final int FIELDS_PER_FLUSH = 50;
    public static final int OP_MOVE = 0;
    public static final int OP_LOADK = 1;
    public static final int OP_LOADBOOL = 2;
    public static final int OP_LOADNIL = 3;
    public static final int OP_GETUPVAL = 4;
    public static final int OP_GETGLOBAL = 5;
    public static final int OP_GETTABLE = 6;
    public static final int OP_SETGLOBAL = 7;
    public static final int OP_SETUPVAL = 8;
    public static final int OP_SETTABLE = 9;
    public static final int OP_NEWTABLE = 10;
    public static final int OP_SELF = 11;
    public static final int OP_ADD = 12;
    public static final int OP_SUB = 13;
    public static final int OP_MUL = 14;
    public static final int OP_DIV = 15;
    public static final int OP_MOD = 16;
    public static final int OP_POW = 17;
    public static final int OP_UNM = 18;
    public static final int OP_NOT = 19;
    public static final int OP_LEN = 20;
    public static final int OP_CONCAT = 21;
    public static final int OP_JMP = 22;
    public static final int OP_EQ = 23;
    public static final int OP_LT = 24;
    public static final int OP_LE = 25;
    public static final int OP_TEST = 26;
    public static final int OP_TESTSET = 27;
    public static final int OP_CALL = 28;
    public static final int OP_TAILCALL = 29;
    public static final int OP_RETURN = 30;
    public static final int OP_FORLOOP = 31;
    public static final int OP_FORPREP = 32;
    public static final int OP_TFORLOOP = 33;
    public static final int OP_SETLIST = 34;
    public static final int OP_CLOSE = 35;
    public static final int OP_CLOSURE = 36;
    public static final int OP_VARARG = 37;
    public static final int OPS_COUNT = 38;

    public LuaThread currentThread;

    // Needed for Math lib - every state needs its own random
    public final Random random = new Random();

    private final LuaTable userdataMetatables;
    private final LuaTable classMetatables;

    static final int MAX_INDEX_RECURSION = 100;

    private static final String meta_ops[];

    static {
        meta_ops = new String[OPS_COUNT];
        meta_ops[OP_ADD] = "__add";
        meta_ops[OP_SUB] = "__sub";
        meta_ops[OP_MUL] = "__mul";
        meta_ops[OP_DIV] = "__div";
        meta_ops[OP_MOD] = "__mod";
        meta_ops[OP_POW] = "__pow";

        meta_ops[OP_EQ] = "__eq";
        meta_ops[OP_LT] = "__lt";
        meta_ops[OP_LE] = "__le";
    }

    public LuaState() {
        // The userdataMetatables must be weak to avoid memory leaks
        LuaTable weakKeyMetatable = new LuaTable();
        weakKeyMetatable.rawset("__mode", "k");
        userdataMetatables = new LuaTable();
        userdataMetatables.setMetatable(weakKeyMetatable);

        classMetatables = new LuaTable();

        currentThread = new LuaThread(this, new LuaTable());

        getEnvironment().rawset("_G", getEnvironment());
        getEnvironment().rawset("_VERSION", "Lua 5.1");

        BaseLib.register(this);
        StringLib.register(this);
        MathLib.register(this);
        CoroutineLib.register(this);
        TableLib.register(this);
        LuaCompiler.register(this);

        LuaClosure closure;
        try {
            closure = LuaCompiler.loadis(this.getClass().getResourceAsStream("/lua/stdlib.lua"), "stdlib", getEnvironment());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        if (closure == null) {
            BaseLib.fail("Could not load stdlib");
        }
        call(closure);
    }

    public void call(Object fun) {
        int base = currentThread.getTop();
        
        if (currentThread.getTop() != 0) {
            throw new RuntimeException("call expects an empty stack.");
        }

        if (fun == null) {
            throw new RuntimeException("tried to call nil");
        } else if (fun instanceof JavaFunction) {
            callJava((JavaFunction) fun, base, base, 0);
        } else if (fun instanceof LuaClosure) {
            LuaCallFrame callFrame = currentThread.pushNewCallFrame((LuaClosure) fun, null, base, base, 0, false, false);
            callFrame.init();
            callFrame.fixedRetCount = -1;

            if (!luaMainloop(-1)) {
                throw new RuntimeException("Unexpected tick exhaustion.");
            }

            currentThread.stackTrace = "";
        } else {
            throw new RuntimeException("tried to call a non-function");
        }

        currentThread.setTop(base);
    }

    public void startCall(Object fun) {
        if (!(fun instanceof LuaClosure)) {
            throw new RuntimeException("startCall can only take a lua function.");
        }
        if (currentThread.getTop() != 0) {
            throw new RuntimeException("startCall expects an empty stack.");
        }
        LuaCallFrame callFrame = currentThread.pushNewCallFrame((LuaClosure) fun, null, 0, 0, 0, false, false);
        callFrame.init();
        callFrame.fixedRetCount = -1;
    }

    public boolean continueCall(int maxTicks) {
        if (luaMainloop(maxTicks)) {
            currentThread.stackTrace = "";
            currentThread.setTop(0);
            return true;
        } else {
            return false;
        }
    }

    private int callJava(JavaFunction f, int localBase, int returnBase,
            int nArguments) {
        LuaThread thread = currentThread;

        LuaCallFrame callFrame = thread.pushNewCallFrame(null, f, localBase,
                returnBase, nArguments, false, false);

        int nReturnValues = f.call(callFrame, nArguments);

        // Clean up return values
        int top = callFrame.getTop();
        int actualReturnBase = top - nReturnValues;

        int diff = returnBase - localBase;
        callFrame.stackCopy(actualReturnBase, diff, nReturnValues);
        callFrame.setTop(nReturnValues + diff);

        thread.popCallFrame();

        return nReturnValues;
    }

    private Object prepareMetatableCall(Object o) {
        if (o instanceof JavaFunction || o instanceof LuaClosure) {
            return o;
        } else {
            return getMetaOp(o, "__call");
        }
    }

    private boolean ismainloop = false;

    // returns true if an actual return happened, as opposed to a tick exhaustion.
    private boolean luaMainloop(int maxTicks) { // TODO: See if this can be cleaned up at all.
        if (ismainloop) {
            throw new RuntimeException("Called luaMainloop from within luaMainloop!");
        }
        try {
            LuaCallFrame callFrame = currentThread.currentCallFrame();
            LuaClosure closure = callFrame.closure;
            LuaPrototype prototype = closure.prototype;
            int[] opcodes = prototype.code;

            int returnBase = callFrame.returnBase;

            while (true) {
                if (maxTicks != -1 && maxTicks-- == 0) {
                    return false;
                }
                try {
                    int a, b, c;

                    int op = opcodes[callFrame.pc++];
                    int opcode = op & 63;

                    currentThread.needsContextRestore = false;

                    if (opcode != OP_CONCAT) {
                        callFrame.concatStatus = -2;
                    }

                    switch (opcode) {
                        case OP_MOVE: {
                            a = getA8(op);
                            b = getB9(op);
                            callFrame.set(a, callFrame.get(b));
                            break;
                        }
                        case OP_LOADK: {
                            a = getA8(op);
                            b = getBx(op);
                            callFrame.set(a, prototype.constants[b]);
                            break;
                        }
                        case OP_LOADBOOL: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);
                            Boolean bool = b == 0 ? Boolean.FALSE : Boolean.TRUE;
                            callFrame.set(a, bool);
                            if (c != 0) {
                                callFrame.pc++;
                            }
                            break;
                        }
                        case OP_LOADNIL: {
                            a = getA8(op);
                            b = getB9(op);
                            callFrame.stackClear(a, b);
                            break;
                        }
                        case OP_GETUPVAL: {
                            a = getA8(op);
                            b = getB9(op);
                            UpValue uv = closure.upvalues[b];
                            callFrame.set(a, uv.getValue());
                            break;
                        }
                        case OP_GETGLOBAL: {
                            a = getA8(op);
                            b = getBx(op);

                            callFrame.postProcess = false;

                            callFrame = tableGetDele(a, closure.env, prototype.constants[b], callFrame);
                            break;
                        }
                        case OP_GETTABLE: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            Object bObj = callFrame.get(b);

                            Object key = getRegisterOrConstant(callFrame, c, prototype);

                            callFrame.postProcess = false;

                            callFrame = tableGetDele(a, bObj, key, callFrame);
                            break;
                        }
                        case OP_SELF: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            Object bObj = callFrame.get(b);

                            Object key = getRegisterOrConstant(callFrame, c, prototype);

                            callFrame.set(a + 1, bObj); // done once now and once after return, if needed.
                            callFrame.postProcess = true; // setting [a+1] to bObj
                            callFrame.postProcessArg = bObj;

                            callFrame = tableGetDele(a, bObj, key, callFrame);
                            break;
                        }
                        case OP_SETGLOBAL: {
                            a = getA8(op);
                            b = getBx(op);
                            Object value = callFrame.get(a);
                            Object key = prototype.constants[b];

                            callFrame = tableSetDele(closure.env, key, value, callFrame);
                            break;
                        }
                        case OP_SETTABLE: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            Object aObj = callFrame.get(a);

                            Object key = getRegisterOrConstant(callFrame, b, prototype);
                            Object value = getRegisterOrConstant(callFrame, c, prototype);

                            callFrame = tableSetDele(aObj, key, value, callFrame);
                            break;
                        }
                        case OP_SETUPVAL: {
                            a = getA8(op);
                            b = getB9(op);

                            UpValue uv = closure.upvalues[b];
                            uv.setValue(callFrame.get(a));

                            break;
                        }
                        case OP_NEWTABLE: {
                            a = getA8(op);

                            // Used to set up initial array and hash size - not implemented
                            // b = getB9(op);
                            // c = getC9(op);
                            LuaTable t = new LuaTable();
                            callFrame.set(a, t);
                            break;
                        }
                        case OP_ADD:
                        case OP_SUB:
                        case OP_MUL:
                        case OP_DIV:
                        case OP_MOD:
                        case OP_POW: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            Object bo = getRegisterOrConstant(callFrame, b, prototype);
                            Object co = getRegisterOrConstant(callFrame, c, prototype);

                            Double bd = null, cd = null;
                            if ((bd = BaseLib.rawTonumber(bo)) == null
                                    || (cd = BaseLib.rawTonumber(co)) == null) {
                                String meta_op = meta_ops[opcode];

                                Object metafun = getBinMetaOp(bo, co, meta_op);
                                if (metafun == null) {
                                    BaseLib.fail((meta_op + " not defined for operands"));
                                }
                                int top = currentThread.getTop();
                                currentThread.setTop(top + 2);
                                currentThread.objectStack[top + 0] = bo;
                                currentThread.objectStack[top + 1] = co;

                                callFrame.postProcess = false;

                                callFrame.fixedRetCount = 1;

                                callFrame = callInternalDele(metafun, top, callFrame.localBase + a, 2, true, callFrame);
                            } else {
                                callFrame.set(a, primitiveMath(bd, cd, opcode));
                            }
                            break;
                        }
                        case OP_UNM: {
                            a = getA8(op);
                            b = getB9(op);
                            Object aObj = callFrame.get(b);

                            Double aDouble = BaseLib.rawTonumber(aObj);
                            if (aDouble != null) {
                                callFrame.set(a, -aDouble);
                            } else {
                                Object metafun = getMetaOp(aObj, "__unm");
                                BaseLib.luaAssert(metafun != null, "__unm not defined for operand");

                                int top = currentThread.getTop();
                                currentThread.setTop(top + 1);
                                currentThread.objectStack[top + 0] = aObj;

                                callFrame.postProcess = false;

                                callFrame.fixedRetCount = 1;

                                callFrame = callInternalDele(metafun, top, callFrame.localBase + a, 1, true, callFrame);
                            }
                            break;
                        }
                        case OP_NOT: {
                            a = getA8(op);
                            b = getB9(op);
                            Object aObj = callFrame.get(b);
                            callFrame.set(a, Boolean.valueOf(!boolEval(aObj)));
                            break;
                        }
                        case OP_LEN: {
                            a = getA8(op);
                            b = getB9(op);

                            Object o = callFrame.get(b);
                            if (o instanceof LuaTable) {
                                LuaTable t = (LuaTable) o;
                                callFrame.set(a, (double) t.len());
                            } else if (o instanceof String) {
                                String s = (String) o;
                                callFrame.set(a, (double) s.length());
                            } else {
                                Object f = getMetaOp(o, "__len");
                                BaseLib.luaAssert(f != null, "__len not defined for operand");

                                int top = currentThread.getTop();
                                currentThread.setTop(top + 1);
                                currentThread.objectStack[top + 0] = o;

                                callFrame.postProcess = false;

                                callFrame.fixedRetCount = 1;

                                callFrame = callInternalDele(f, top, callFrame.localBase + a, 1, true, callFrame);
                            }
                            break;
                        }
                        case OP_CONCAT: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            if (callFrame.concatStatus == -2) {
                                callFrame.concatStatus = c;
                                callFrame.concatState = callFrame.get(callFrame.concatStatus--);
                            }
                            if (b <= callFrame.concatStatus) {
                                String resStr = BaseLib.rawTostring(callFrame.concatState);
                                String lastStr = BaseLib.rawTostring(callFrame.get(callFrame.concatStatus));
                                if (resStr != null && lastStr != null) {
                                    callFrame.concatState = lastStr + resStr;
                                    callFrame.concatStatus--;
                                    callFrame.pc--;
                                } else {
                                    Object leftConcat = callFrame.get(callFrame.concatStatus);

                                    Object metafun = getBinMetaOp(leftConcat, callFrame.concatState, "__concat");
                                    if (metafun == null) {
                                        BaseLib.fail(("__concat not defined for operands: " + leftConcat + " and " + callFrame.concatState));
                                    }
                                    int oldTop = currentThread.getTop();
                                    currentThread.setTop(oldTop + 2);
                                    currentThread.objectStack[oldTop] = leftConcat;
                                    currentThread.objectStack[oldTop + 1] = callFrame.concatState;

                                    callFrame.concatStatus--;
                                    callFrame.pc--;

                                    callFrame.postProcess = true;
                                    callFrame.postProcessArg = oldTop;

                                    callFrame.fixedRetCount = -1;

                                    LuaCallFrame cf = callFrame;

                                    callFrame = callInternalDele(metafun, oldTop, oldTop, 2, false, callFrame);

                                    if (cf == callFrame) { // java call
                                        currentThread.setTop(oldTop);
                                        callFrame.concatState = currentThread.objectStack[oldTop];
                                    }
                                }
                            } else {
                                callFrame.set(a, callFrame.concatState);
                                callFrame.concatStatus = -2;
                            }
                            break;
                        }
                        case OP_JMP: {
                            callFrame.pc += getSBx(op);
                            break;
                        }
                        case OP_EQ:
                        case OP_LT:
                        case OP_LE: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            Object bo = getRegisterOrConstant(callFrame, b, prototype);
                            Object co = getRegisterOrConstant(callFrame, c, prototype);

                            if (bo instanceof Double && co instanceof Double) {
                                double bd_primitive = (Double) bo;
                                double cd_primitive = (Double) co;

                                if (opcode == OP_EQ) {
                                    if ((bd_primitive == cd_primitive) == (a == 0)) {
                                        callFrame.pc++;
                                    }
                                } else {
                                    if (opcode == OP_LT) {
                                        if ((bd_primitive < cd_primitive) == (a == 0)) {
                                            callFrame.pc++;
                                        }
                                    } else { // opcode must be OP_LE
                                        if ((bd_primitive <= cd_primitive) == (a == 0)) {
                                            callFrame.pc++;
                                        }
                                    }
                                }
                            } else if (bo instanceof String && co instanceof String) {
                                if (opcode == OP_EQ) {
                                    if ((bo.equals(co)) == (a == 0)) {
                                        callFrame.pc++;
                                    }
                                } else {
                                    String bs = (String) bo;
                                    String cs = (String) co;
                                    int cmp = bs.compareTo(cs);

                                    if (opcode == OP_LT) {
                                        if ((cmp < 0) == (a == 0)) {
                                            callFrame.pc++;
                                        }
                                    } else { // opcode must be OP_LE
                                        if ((cmp <= 0) == (a == 0)) {
                                            callFrame.pc++;
                                        }
                                    }
                                }
                            } else {
                                if (bo == co) {
                                    if (a == 0) {
                                        callFrame.pc++;
                                    }
                                } else {
                                    boolean invert = false;

                                    String meta_op = meta_ops[opcode];

                                    Object metafun = getCompMetaOp(bo, co, meta_op);

                                    /*
                                     * Special case: OP_LE uses OP_LT if __le is not
                                     * defined. a <= b is then translated to not (b < a)
                                     */
                                    if (metafun == null && opcode == OP_LE) {
                                        metafun = getCompMetaOp(bo, co, "__lt");

                                        // Swap the objects
                                        Object tmp = bo;
                                        bo = co;
                                        co = tmp;

                                        // Invert a (i.e. add the "not"
                                        invert = true;
                                    }

                                    if (metafun == null) {
                                        if (opcode == OP_EQ) {
                                            boolean eq = LuaState.luaEquals(bo, co);
                                            if ((eq ^ invert) == (a == 0)) {
                                                callFrame.pc++;
                                            }
                                        } else {
                                            BaseLib.fail((meta_op + " not defined for operand"));
                                        }
                                    } else {
                                        int oldTop = currentThread.getTop();
                                        currentThread.setTop(oldTop + 2);
                                        currentThread.objectStack[oldTop] = bo;
                                        currentThread.objectStack[oldTop + 1] = co;

                                        callFrame.postProcess = true;
                                        callFrame.postProcessArg = invert ^ (a == 0) ? oldTop | 0x80000000 : oldTop;
                                        callFrame.fixedRetCount = -1;

                                        LuaCallFrame cf = callFrame;

                                        callFrame = callInternalDele(metafun, oldTop, oldTop, 2, false, callFrame);

                                        if (cf == callFrame) {
                                            currentThread.setTop(oldTop);

                                            if ((boolEval(currentThread.objectStack[oldTop]) ^ invert) == (a == 0)) {
                                                callFrame.pc++;
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        }
                        case OP_TEST: {
                            a = getA8(op);
                            // b = getB9(op);
                            c = getC9(op);

                            Object value = callFrame.get(a);
                            if (boolEval(value) == (c == 0)) {
                                callFrame.pc++;
                            }

                            break;
                        }
                        case OP_TESTSET: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            Object value = callFrame.get(b);
                            if (boolEval(value) != (c == 0)) {
                                callFrame.set(a, value);
                            } else {
                                callFrame.pc++;
                            }

                            break;
                        }
                        case OP_CALL: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);
                            int nArguments2 = b - 1;
                            if (nArguments2 != -1) {
                                callFrame.setTop(a + nArguments2 + 1);
                            } else {
                                nArguments2 = callFrame.getTop() - a - 1;
                            }

                            int base = callFrame.localBase;

                            int localBase2 = base + a + 1;
                            int returnBase2 = base + a;

                            Object funObject = callFrame.get(a);
                            BaseLib.luaAssert(funObject != null, "Tried to call nil");
                            Object fun = prepareMetatableCall(funObject);
                            if (fun == null) {
                                BaseLib.fail(("Object " + funObject + " did not have __call metatable set"));
                            }

                            // If it's a metatable __call, prepend the caller as the
                            // first argument
                            if (fun != funObject) {
                                localBase2 = returnBase2;
                                nArguments2++;
                            }

                            callFrame.postProcess = false;
                            callFrame.fixedRetCount = -1;

                            callFrame = callInternalDele(fun, localBase2, returnBase2, nArguments2, c != 0, callFrame);
                            break;
                        }
                        case OP_TAILCALL: {
                            int base = callFrame.localBase;

                            currentThread.closeUpvalues(base);

                            a = getA8(op);
                            b = getB9(op);
                            int nArguments2 = b - 1;
                            if (nArguments2 == -1) {
                                nArguments2 = callFrame.getTop() - a - 1;
                            }

                            callFrame.restoreTop = false;
                            callFrame.fixedRetCount = -1;
                            callFrame.postProcess = false;

                            Object funObject = callFrame.get(a);
                            BaseLib.luaAssert(funObject != null, "Tried to call nil");
                            Object fun = prepareMetatableCall(funObject);
                            if (!(fun != null)) {
                                BaseLib.fail(("Object " + funObject + " did not have __call metatable set"));
                            }

                            int localBase2 = returnBase + 1;

                            // If it's a metatable __call, prepend the caller as the
                            // first argument
                            if (fun != funObject) {
                                localBase2 = returnBase;
                                nArguments2++;
                            }

                            currentThread.stackCopy(base + a, returnBase,
                                    nArguments2 + 1);
                            currentThread.setTop(returnBase + nArguments2 + 1);

                            if (fun instanceof LuaClosure) {
                                callFrame.localBase = localBase2;
                                callFrame.nArguments = nArguments2;
                                callFrame.closure = (LuaClosure) fun;
                                callFrame.init();
                            } else {
                                if (!(fun instanceof JavaFunction)) {
                                    BaseLib.fail(("Tried to call a non-function: " + fun));
                                }
                                LuaThread oldThread = currentThread;
                                callJava((JavaFunction) fun, localBase2, returnBase,
                                        nArguments2);

                                callFrame = currentThread.currentCallFrame();
                                oldThread.popCallFrame();

                                if (oldThread != currentThread) {
                                    if (oldThread.isDead()) {

                                        if (currentThread.parent == oldThread) {
                                            currentThread.parent = oldThread.parent;
                                            oldThread.parent = null;

                                            // This is an implicit yield, so push a TRUE
                                            // to the parent
                                            currentThread.parent.currentCallFrame().push(Boolean.TRUE);
                                        }
                                    }

                                    callFrame = currentThread.currentCallFrame();
                                    if (callFrame.isJava()) {
                                        return true;
                                    }
                                } else {
                                    if (!callFrame.fromLua) {
                                        return true;
                                    }
                                    callFrame = currentThread.currentCallFrame();

                                    if (callFrame.restoreTop) {
                                        callFrame.setTop(callFrame.closure.prototype.maxStacksize);
                                    }
                                }
                            }

                            closure = callFrame.closure;
                            prototype = closure.prototype;
                            opcodes = prototype.code;
                            returnBase = callFrame.returnBase;

                            break;
                        }
                        case OP_RETURN: {
                            a = getA8(op);
                            b = getB9(op) - 1;

                            int base = callFrame.localBase;
                            currentThread.closeUpvalues(base);

                            if (b == -1) {
                                b = callFrame.getTop() - a;
                            }

                            int frc = currentThread.parentCallFrame() != null ? currentThread.parentCallFrame().fixedRetCount : -1;
                            if (frc != -1 && frc < b) {
                                b = frc;
                            }

                            currentThread.stackCopy(callFrame.localBase + a,
                                    returnBase, b);
                            currentThread.setTop(returnBase + b);

                            if (!callFrame.fromLua) {
                                currentThread.popCallFrame();
                                return true;
                            }
                            if (callFrame.insideCoroutine
                                    && currentThread.callFrameTop == 1) {
                                callFrame.localBase = callFrame.returnBase;
                                LuaThread thread = currentThread;
                                CoroutineLib.yieldHelper(callFrame, callFrame, b);
                                thread.popCallFrame();

                                // If this thread is called from a java function,
                                // return immediately
                                callFrame = currentThread.currentCallFrame();
                                if (callFrame.isJava()) {
                                    return true;
                                }
                            } else {
                                currentThread.popCallFrame();
                                callFrame = currentThread.currentCallFrame();
                            }

                            closure = callFrame.closure;
                            prototype = closure.prototype;
                            opcodes = prototype.code;
                            returnBase = callFrame.returnBase;

                            if (callFrame.restoreTop) {
                                callFrame.setTop(prototype.maxStacksize);
                            }
                            if (callFrame.postProcess) {
                                int lop = opcodes[callFrame.pc - 1];
                                switch (lop & 63) {
                                    case OP_SELF:
                                        callFrame.set(getA8(lop), callFrame.postProcessArg);
                                        break;
                                    case OP_TFORLOOP: // duplicated in OP_TFORLOOP
                                        callFrame.clearFromIndex(getA8(lop) + 3 + getC9(lop));
                                        callFrame.setPrototypeStacksize();

                                        Object aObj3 = callFrame.get(getA8(lop) + 3);
                                        if (aObj3 != null) {
                                            callFrame.set(getA8(lop) + 2, aObj3);
                                        } else {
                                            callFrame.pc++;
                                        }
                                        break;
                                    case OP_EQ:
                                    case OP_LT:
                                    case OP_LE:
                                        int oldtop = (Integer) callFrame.postProcessArg;

                                        boolean invert = false;
                                        if ((oldtop & 0x80000000) != 0) {
                                            invert = true;
                                            oldtop &= 0x7FFFFFFF;
                                        }

                                        if (boolEval(currentThread.objectStack[oldtop]) == invert) {
                                            callFrame.pc++;
                                        }

                                        currentThread.setTop(oldtop);
                                        break;
                                    default:
                                        if (callFrame.concatStatus != -2 && (opcodes[callFrame.pc] & 63) == OP_CONCAT) {
                                            int otop = (Integer) callFrame.postProcessArg;
                                            callFrame.concatState = currentThread.objectStack[otop];
                                            currentThread.setTop(otop);
                                            break;
                                        }
                                        throw new Error("Invalid postProcess callback.");
                                }
                                callFrame.postProcessArg = null;
                            }
                            break;
                        }
                        case OP_FORPREP: {
                            a = getA8(op);
                            b = getSBx(op);

                            double iter = (Double) callFrame.get(a);
                            double step = (Double) callFrame.get(a + 2);
                            callFrame.set(a, iter - step);
                            callFrame.pc += b;
                            break;
                        }
                        case OP_FORLOOP: {
                            a = getA8(op);

                            double iter = (Double) callFrame.get(a);
                            double end = (Double) callFrame.get(a + 1);
                            double step = (Double) callFrame.get(a + 2);
                            iter += step;
                            Double iterDouble = iter;
                            callFrame.set(a, iterDouble);

                            if ((step > 0) ? iter <= end : iter >= end) {
                                b = getSBx(op);
                                callFrame.pc += b;
                                callFrame.set(a + 3, iterDouble);
                            } else {
                                callFrame.clearFromIndex(a);
                            }
                            break;
                        }
                        case OP_TFORLOOP: {
                            a = getA8(op);
                            c = getC9(op);

                            callFrame.setTop(a + 6);
                            callFrame.stackCopy(a, a + 3, 3);

                            int top = currentThread.getTop();
                            int base = top - 3;

                            callFrame.postProcess = true;

                            callFrame.fixedRetCount = c;

                            LuaCallFrame cf = callFrame;

                            callFrame = callInternalDele(currentThread.objectStack[base], base + 1, base, 2, false, callFrame);

                            // duplicated above
                            if (cf == callFrame) { // direct return
                                callFrame.clearFromIndex(getA8(op) + 3 + getC9(op));
                                callFrame.setPrototypeStacksize();

                                Object aObj3 = callFrame.get(getA8(op) + 3);
                                if (aObj3 != null) {
                                    callFrame.set(getA8(op) + 2, aObj3);
                                } else {
                                    callFrame.pc++;
                                }
                            }
                            break;
                        }
                        case OP_SETLIST: {
                            a = getA8(op);
                            b = getB9(op);
                            c = getC9(op);

                            if (b == 0) {
                                b = callFrame.getTop() - a - 1;
                            }

                            if (c == 0) {
                                c = opcodes[callFrame.pc++];
                            }

                            int offset = (c - 1) * FIELDS_PER_FLUSH;

                            LuaTable t = (LuaTable) callFrame.get(a);
                            for (int i = 1; i <= b; i++) {
                                Object key = (double) (offset + i);
                                Object value = callFrame.get(a + i);
                                t.rawset(key, value);
                            }
                            break;
                        }
                        case OP_CLOSE: {
                            a = getA8(op);
                            callFrame.closeUpvalues(a);
                            break;
                        }
                        case OP_CLOSURE: {
                            a = getA8(op);
                            b = getBx(op);
                            LuaPrototype newPrototype = prototype.prototypes[b];
                            LuaClosure newClosure = new LuaClosure(newPrototype, closure.env);
                            callFrame.set(a, newClosure);
                            int numUpvalues = newPrototype.numUpvalues;
                            for (int i = 0; i < numUpvalues; i++) {
                                op = opcodes[callFrame.pc++];
                                opcode = op & 63;
                                b = getB9(op);
                                switch (opcode) {
                                    case OP_MOVE: {
                                        newClosure.upvalues[i] = callFrame.findUpvalue(b);
                                        break;
                                    }
                                    case OP_GETUPVAL: {
                                        newClosure.upvalues[i] = closure.upvalues[b];
                                        break;
                                    }
                                    default:
                                    // should never happen
                                }
                            }
                            break;
                        }
                        case OP_VARARG: {
                            a = getA8(op);
                            b = getB9(op) - 1;

                            callFrame.pushVarargs(a, b);
                            break;
                        }
                        default: {
                            // unreachable for proper bytecode
                            throw new Error("improper bytecode");
                        }
                    } // switch
                    if (currentThread.needsContextRestore) {
                        // This means that we got back from a yield to a java
                        // function, such as pcall
                        if (callFrame.isJava()) {
                            return true;
                        }

                        closure = callFrame.closure;
                        prototype = closure.prototype;
                        opcodes = prototype.code;
                        returnBase = callFrame.returnBase;
                    }
                } catch (Throwable e) {
                    // Pop off all java frames first
                    while (true) {
                        callFrame = currentThread.currentCallFrame();

                        if (callFrame.isLua()) {
                            break;
                        }
                        currentThread.addStackTrace(callFrame);
                        currentThread.popCallFrame();
                    }

                    boolean rethrow = true;
                    while (true) {
                        callFrame = currentThread.currentCallFrame();
                        if (callFrame == null) {
                            LuaThread parent = currentThread.parent;
                            if (parent != null) {
                                currentThread.parent = null;
                                // Close all live upvalues before yielding
                                currentThread.closeUpvalues(0);

                                // Yield and fail
                                // Copy arguments
                                LuaCallFrame nextCallFrame = parent
                                        .currentCallFrame();

                                nextCallFrame.push(Boolean.FALSE);
                                nextCallFrame.push(e.getMessage());
                                nextCallFrame.push(currentThread.stackTrace);

                                currentThread.state.currentThread = parent;
                                currentThread = parent;
                                callFrame = currentThread.currentCallFrame();
                                closure = callFrame.closure;
                                prototype = closure.prototype;
                                opcodes = prototype.code;
                                returnBase = callFrame.returnBase;

                                rethrow = false;
                                // Note: previously closing live upvalues ALSO happened ~after~ switching threads.
                                // I (Colby Skeggs) think that was wrong, so now it happens ~before~ switching threads.
                            }
                            break;
                        }
                        currentThread.addStackTrace(callFrame);
                        if (callFrame.closure != null && callFrame.closure.prototype.isExceptionHandler) {
                            currentThread.closeUpvalues(callFrame.localBase);

                            currentThread.objectStack[callFrame.returnBase] = Boolean.FALSE;
                            currentThread.objectStack[callFrame.returnBase + 1] = e instanceof LuaException ? ((LuaException) e).errorMessage : e.getMessage();
                            currentThread.objectStack[callFrame.returnBase + 2] = currentThread.stackTrace;
                            currentThread.objectStack[callFrame.returnBase + 3] = e;
                            currentThread.setTop(callFrame.returnBase + 4);

                            currentThread.popCallFrame();
                            callFrame = currentThread.currentCallFrame();

                            currentThread.stackTrace = "";

                            closure = callFrame.closure;
                            prototype = closure.prototype;
                            opcodes = prototype.code;
                            returnBase = callFrame.returnBase;

                            if (callFrame.restoreTop) {
                                callFrame.setTop(prototype.maxStacksize);
                            }
                            rethrow = false;
                            break;
                        }
                        currentThread.popCallFrame();

                        if (!callFrame.fromLua) {
                            break;
                        }
                    }
                    if (rethrow) {
                        // Close all live upvalues before rethrowing
                        if (callFrame != null) {
                            callFrame.closeUpvalues(0);
                        }
                        throw e;
                    } else if (callFrame == null) {
                        throw new NullPointerException("callFrame became null!");
                    }
                }
            }
        } finally {
            ismainloop = false;
        }
    }

    private LuaCallFrame callInternalDele(Object fun, int localBase2, int returnBase2, int nArguments2, boolean restoreTop, LuaCallFrame callFrame) throws RuntimeException {
        callFrame.restoreTop = restoreTop;
        if (fun instanceof LuaClosure) {
            LuaCallFrame newCallFrame = currentThread
                    .pushNewCallFrame((LuaClosure) fun, null, localBase2,
                            returnBase2, nArguments2, true,
                            callFrame.insideCoroutine);
            newCallFrame.init();

            currentThread.needsContextRestore = true;

            return newCallFrame;
        } else if (fun instanceof JavaFunction) {
            callJava((JavaFunction) fun, localBase2, returnBase2, nArguments2);

            callFrame = currentThread.currentCallFrame();

            if (!callFrame.isJava() && callFrame.restoreTop) {
                callFrame.setTop(callFrame.closure.prototype.maxStacksize);
            }

            currentThread.needsContextRestore = true;

            return callFrame;
        } else {
            throw new RuntimeException("Tried to call a non-function: " + fun);
        }
    }

    public Object getMetaOp(Object o, String meta_op) {
        LuaTable meta = (LuaTable) getmetatable(o, true);
        return meta == null ? null : meta.rawget(meta_op);
    }

    private Object getCompMetaOp(Object a, Object b, String meta_op) {
        LuaTable meta1 = (LuaTable) getmetatable(a, true);
        LuaTable meta2 = (LuaTable) getmetatable(b, true);
        if (meta1 != meta2 || meta1 == null) { // TODO: I'm not sure that this exactly matches the Lua spec.
            return null;
        }
        return meta1.rawget(meta_op);
    }

    private Object getBinMetaOp(Object a, Object b, String meta_op) {
        Object op = getMetaOp(a, meta_op);
        if (op != null) {
            return op;
        }
        return getMetaOp(b, meta_op);
    }

    private Object getRegisterOrConstant(LuaCallFrame callFrame, int index, LuaPrototype prototype) {
        int cindex = index - 256;
        if (cindex < 0) {
            return callFrame.get(index);
        } else {
            return prototype.constants[cindex];
        }
    }

    private static int getA8(int op) {
        return (op >>> 6) & 255;
    }

    private static int getC9(int op) {
        return (op >>> 14) & 511;
    }

    private static int getB9(int op) {
        return (op >>> 23) & 511;
    }

    private static int getBx(int op) {
        return (op >>> 14);
    }

    private static int getSBx(int op) {
        return (op >>> 14) - 131071;
    }

    private Double primitiveMath(double v1, double v2, int opcode) {
        switch (opcode) {
            case OP_ADD:
                return v1 + v2;
            case OP_SUB:
                return v1 - v2;
            case OP_MUL:
                return v1 * v2;
            case OP_DIV:
                return v1 / v2;
            case OP_MOD:
                // TODO: consider using math.fmod?
                if (v2 == 0) {
                    return Double.NaN;
                } else {
                    int ipart = (int) (v1 / v2);
                    return v1 - ipart * v2;
                }
            case OP_POW:
                return MathLib.pow(v1, v2);
            default:
                // this should be unreachable
                throw new Error("invalid aop");
        }
    }

    public LuaCallFrame tableGetDele(int target, Object table, Object key, LuaCallFrame callFrame) {
        Object curObj = table;
        if (curObj == null) {
            throw new NullPointerException();
        }
        int i = LuaState.MAX_INDEX_RECURSION;
        do {
            if (i-- <= 0) {
                throw new RuntimeException("loop in gettable");
            }
            boolean isTable = curObj instanceof LuaTable;
            if (isTable) {
                LuaTable t = (LuaTable) curObj;
                Object res = t.rawget(key);
                if (res != null) {
                    callFrame.set(target, res);
                    return callFrame;
                }
            }
            curObj = getMetaOp(curObj, "__index");
            if (curObj == null) {
                if (isTable) {
                    callFrame.set(target, null);
                    return callFrame;
                }
                throw new RuntimeException("attempted index of non-table: "
                        + curObj);
            }
        } while (!(curObj instanceof JavaFunction || curObj instanceof LuaClosure));

        int top = currentThread.getTop();
        currentThread.setTop(top + 2);
        currentThread.objectStack[top + 0] = table;
        currentThread.objectStack[top + 1] = key;

        callFrame.fixedRetCount = 1;

        return callInternalDele(curObj, top, callFrame.localBase + target, 2, true, callFrame);
    }

    private LuaCallFrame tableSetDele(Object table, Object key, Object value, LuaCallFrame callFrame) {
        Object curObj = table;
        int remaining = LuaState.MAX_INDEX_RECURSION;
        do {
            if (remaining-- <= 0) {
                throw new RuntimeException("loop in settable");
            }
            if (curObj instanceof LuaTable) {
                LuaTable t = (LuaTable) curObj;

                if (t.rawget(key) != null) {
                    t.rawset(key, value);
                    return callFrame;
                }

                curObj = getMetaOp(curObj, "__newindex");
                if (curObj == null) {
                    t.rawset(key, value);
                    return callFrame;
                }
            } else {
                curObj = getMetaOp(curObj, "__newindex");
                BaseLib.luaAssert(curObj != null, "attempted index of non-table");
            }
        } while (!(curObj instanceof JavaFunction || curObj instanceof LuaClosure));

        int top = currentThread.getTop();
        currentThread.setTop(top + 3);
        currentThread.objectStack[top + 0] = table;
        currentThread.objectStack[top + 1] = key;
        currentThread.objectStack[top + 2] = value;

        callFrame.postProcess = false;

        callFrame.fixedRetCount = 0;

        return callInternalDele(curObj, top, top, 3, true, callFrame);
    }

    public LuaTable getClassMetatable(Class clazz) {
        return (LuaTable) classMetatables.rawget(clazz);
    }

    public void setClassMetatable(Class clazz, LuaTable metatable) {
        classMetatables.rawset(clazz, metatable);
    }

    public void setmetatable(Object o, LuaTable metatable) {
        BaseLib.luaAssert(o != null, "Can't set metatable for nil");
        if (o instanceof LuaTable) {
            LuaTable t = (LuaTable) o;
            t.setMetatable(metatable);
        } else {
            userdataMetatables.rawset(o, metatable);
        }
    }

    public Object getmetatable(Object o, boolean raw) {
        if (o == null) {
            return null;
        }
        LuaTable metatable;
        if (o instanceof LuaTable) {
            LuaTable t = (LuaTable) o;
            metatable = t.getMetatable();
        } else {
            metatable = (LuaTable) userdataMetatables.rawget(o);
        }

        if (metatable == null) {
            metatable = (LuaTable) classMetatables.rawget(o.getClass());
        }

        if (!raw && metatable != null) {
            Object meta2 = metatable.rawget("__metatable");
            if (meta2 != null) {
                return meta2;
            }
        }
        return metatable;
    }

    public LuaTable getEnvironment() {
        return currentThread.environment;
    }

    public static boolean luaEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Double && b instanceof Double) {
            double ad = (Double) a;
            double bd = (Double) b;
            return ad == bd;
        }
        return a == b;
    }

    public static boolean boolEval(Object o) {
        return (o != null) && (o != Boolean.FALSE);
    }
}
