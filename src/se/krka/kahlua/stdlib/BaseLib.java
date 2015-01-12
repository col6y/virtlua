/*
 Copyright (c) 2007-2009 Kristofer Karlsson <kristofer.karlsson@gmail.com>

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
package se.krka.kahlua.stdlib;

import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm.LuaClosure;
import se.krka.kahlua.vm.LuaException;
import se.krka.kahlua.vm.LuaState;
import se.krka.kahlua.vm.LuaTable;
import se.krka.kahlua.vm.LuaThread;

public enum BaseLib implements JavaFunction {

    DEBUGSTACKTRACE {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    LuaThread thread = getOptArgThread(callFrame, 1, callFrame.thread);
                    int level = getOptArgInteger(callFrame, 2, 0);
                    int count = getOptArgInteger(callFrame, 3, Integer.MAX_VALUE);
                    int haltAt = getOptArgInteger(callFrame, 4, 0);
                    return callFrame.push(thread.getCurrentStackTrace(level, count, haltAt));
                }
            },
    RAWGET {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 2, "Not enough arguments");
                    return callFrame.push(((LuaTable) callFrame.get(0)).rawget(callFrame.get(1)));
                }
            },
    RAWSET {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 3, "Not enough arguments");
                    ((LuaTable) callFrame.get(0)).rawset(callFrame.get(1), callFrame.get(2));
                    callFrame.setTop(1);
                    return 1;
                }
            },
    RAWEQUAL {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 2, "Not enough arguments");
                    return callFrame.push(LuaState.luaEquals(callFrame.get(0), callFrame.get(1)));
                }
            },
    SETFENV {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 2, "Not enough arguments");

                    LuaTable newEnv = (LuaTable) callFrame.get(1);
                    luaAssert(newEnv != null, "expected a table");

                    LuaClosure closure;

                    Object o = callFrame.get(0);
                    if (o instanceof LuaClosure) {
                        closure = (LuaClosure) o;
                    } else {
                        o = rawTonumber(o);
                        luaAssert(o != null, "expected a lua function or a number");
                        int level = ((Double) o).intValue();
                        if (level == 0) {
                            callFrame.thread.environment = newEnv;
                            return 0;
                        }
                        LuaCallFrame parentCallFrame = callFrame.thread.getParent(level);
                        luaAssert(parentCallFrame.isLua(), "No closure found at this level: " + level);
                        closure = parentCallFrame.closure;
                    }

                    closure.env = newEnv;

                    callFrame.setTop(1);
                    return 1;
                }
            },
    GETFENV {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    Object o = nArguments >= 1 ? callFrame.get(0) : 1.0;

                    Object res;
                    if (o == null || o instanceof JavaFunction) {
                        res = callFrame.thread.environment;
                    } else if (o instanceof LuaClosure) {
                        res = ((LuaClosure) o).env;
                    } else {
                        Double d = rawTonumber(o);
                        luaAssert(d != null, "Expected number");
                        int level = d.intValue();
                        luaAssert(level >= 0, "level must be non-negative");
                        res = callFrame.thread.getParent(level).getEnvironment();
                    }
                    return callFrame.push(res);
                }
            },
    NEXT {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");

                    LuaTable t = (LuaTable) callFrame.get(0);
                    Object key = nArguments >= 2 ? callFrame.get(1) : null;

                    Object nextKey = t.next(key);
                    if (nextKey == null) {
                        callFrame.setTop(1);
                        callFrame.set(0, null);
                        return 1;
                    }

                    Object value = t.rawget(nextKey);

                    callFrame.setTop(2);
                    callFrame.set(0, nextKey);
                    callFrame.set(1, value);
                    return 2;
                }
            },
    UNPACK {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");

                    LuaTable t = (LuaTable) callFrame.get(0);

                    Object di = nArguments >= 2 ? callFrame.get(1) : null;
                    Object dj = nArguments >= 3 ? callFrame.get(2) : null;

                    int i = di != null ? ((Double) di).intValue() : 1;
                    int j = dj != null ? ((Double) dj).intValue() : t.len();

                    int nReturnValues = 1 + j - i;

                    if (nReturnValues <= 0) {
                        callFrame.setTop(0);
                        return 0;
                    }

                    callFrame.setTop(nReturnValues);
                    for (int b = 0; b < nReturnValues; b++) {
                        callFrame.set(b, t.rawget((double) (i + b)));
                    }
                    return nReturnValues;
                }
            },
    ERROR {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    // TODO: Figure out why this doesn't match specifications. Maybe fix it.
                    if (nArguments >= 1) {
                        String stacktrace = (String) getOptArg(callFrame, 2, BaseLib.TYPE_STRING);
                        if (stacktrace == null) {
                            stacktrace = "";
                        }
                        callFrame.thread.stackTrace = stacktrace;
                        throw new LuaException(callFrame.get(0));
                    }
                    return 0;
                }
            },
    _PCALLINIT0 {
                // flags the prototype of a closure as being an exception handler, among other changes.
                // only used in the lua implementation of pcall
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    ((LuaClosure) callFrame.get(0)).prototype.isExceptionHandler = true;
                    for (int i=0; i<nArguments; i++) {
                        stripTailCalls(((LuaClosure) callFrame.get(i)).prototype.code);
                    }
                    return 0;
                }
                
                private void stripTailCalls(int[] code) {
                    for (int i=0; i<code.length; i++) {
                        if ((code[i] & 63) == LuaState.OP_TAILCALL) {
                            code[i] = (code[i] & ~63) | LuaState.OP_CALL;
                        }
                    }
                }
            },
    _PRINT0 { // full implementation in stdlib.lua
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");
                    System.out.println("[LUA] " + (String) callFrame.get(0));
                    return 0;
                }
            },
    SELECT {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");
                    Object arg1 = callFrame.get(0);
                    if ("#".equals(arg1)) {
                        return callFrame.push((double) (nArguments - 1));
                    }
                    int index = rawTonumber(arg1).intValue();
                    if (index >= 1 && index <= nArguments - 1) {
                        return nArguments - index;
                    }
                    return 0;
                }
            },
    GETMETATABLE {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");
                    return callFrame.push(callFrame.thread.state.getmetatable(callFrame.get(0), false));
                }
            },
    SETMETATABLE {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 2, "Not enough arguments");

                    setmetatable(callFrame.thread.state, callFrame.get(0), (LuaTable) callFrame.get(1), false);

                    callFrame.setTop(1);
                    return 1;
                }
            },
    TYPE {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");
                    return callFrame.push(type(callFrame.get(0)));
                }
            },
    _TOSTRING0 {// full implementation in stdlib.lua - this way, we don't have to do any Lua calls from Java.
                // this version returns two values: an additional-work-needed flag and either a data string or metatable function
                // if the flag is false, all work is done and tostring just returns the data string
                // if the flag is true, the tostring function calls the metatable function on the object and then returns that result.
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    Object obj = callFrame.get(0);
                    String str = tostring_common(obj);
                    if (str != null) {
                        return callFrame.push(false, str);
                    }
                    Object tostringFun = callFrame.thread.state.getMetaOp(obj, "__tostring");
                    if (tostringFun == null) {
                        if (obj instanceof LuaTable) {
                            return callFrame.push(false, "table 0x" + System.identityHashCode(obj));
                        } else {
                            throw new RuntimeException("no __tostring found on object: " + obj.getClass());
                        }
                    }
                    return callFrame.push(true, tostringFun);
                }
            },
    TONUMBER {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    luaAssert(nArguments >= 1, "Not enough arguments");

                    if (nArguments == 1) {
                        return callFrame.push(rawTonumber(callFrame.get(0)));
                    }

                    String s = (String) callFrame.get(0);

                    Double radixDouble = rawTonumber(callFrame.get(1));
                    luaAssert(radixDouble != null, "Argument 2 must be a number");

                    double dradix = radixDouble;
                    int radix = (int) dradix;
                    if (radix != dradix) {
                        throw new RuntimeException("base is not an integer");
                    }
                    return callFrame.push(tonumber(s, radix));
                }
            },
    COLLECTGARBAGE {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    Object option = nArguments > 0 ? callFrame.get(0) : null;

                    if (option == null || option.equals("step") || option.equals("collect")) {
                        System.gc();
                        return 0;
                    }

                    if (option.equals("count")) {
                        long freeMemory = RUNTIME.freeMemory();
                        long totalMemory = RUNTIME.totalMemory();
                        callFrame.setTop(3);
                        callFrame.set(0, toKiloBytes(totalMemory - freeMemory));
                        callFrame.set(1, toKiloBytes(freeMemory));
                        callFrame.set(2, toKiloBytes(totalMemory));
                        return 3;
                    }
                    throw new RuntimeException("invalid option: " + option);
                }
            };
    private static final Runtime RUNTIME = Runtime.getRuntime();

    public String getName() {
        return this.name().toLowerCase();
    }

    public static final Object MODE_KEY = "__mode";

    public static final String TYPE_NIL = "nil";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_FUNCTION = "function";
    public static final String TYPE_TABLE = "table";
    public static final String TYPE_THREAD = "thread";
    public static final String TYPE_USERDATA = "userdata";

    public static void register(LuaState state) {
        for (BaseLib f : BaseLib.values()) {
            state.getEnvironment().rawset(f.getName(), f);
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    public static Object getOptArg(LuaCallFrame callFrame, int n, String type) {
        // Outside of stack
        if (n - 1 >= callFrame.getTop()) {
            return null;
        }

        Object o = callFrame.get(n - 1);
        if (o == null) {
            return null;
        }
        // type coercion
        if (type == TYPE_STRING) {
            return rawTostring(o);
        } else if (type == TYPE_NUMBER) {
            return rawTonumber(o);
        }
        // no type checking, this is optional after all
        return o;
    }

    public static Object getOptArg(LuaCallFrame callFrame, int n, String type, Object default_) {
        Object out = getOptArg(callFrame, n, type);
        return out != null ? out : default_;
    }

    public static LuaThread getOptArgThread(LuaCallFrame callFrame, int n, LuaThread default_) {
        return (LuaThread) getOptArg(callFrame, n, BaseLib.TYPE_THREAD, default_);
    }

    public static double getOptArgNumber(LuaCallFrame callFrame, int n, double default_) {
        return (Double) getOptArg(callFrame, n, BaseLib.TYPE_NUMBER, default_);
    }

    public static int getOptArgInteger(LuaCallFrame callFrame, int n, int default_) {
        return (int) getOptArgNumber(callFrame, n, default_);
    }

    public static void luaAssert(boolean b, String msg) {
        if (!b) {
            fail(msg);
        }
    }

    public static void fail(String msg) {
        throw new RuntimeException(msg);
    }

    public static String numberToString(Double num) {
        if (num.isNaN()) {
            return "nan";
        }
        if (num.isInfinite()) {
            return MathLib.isNegative(num) ? "-inf" : "inf";
        }
        double n = num;
        if (Math.floor(n) == n && Math.abs(n) < 1e14) {
            return String.valueOf(num.longValue());
        } else {
            return Double.toString(n);
        }
    }

    /**
     *
     * @param callFrame
     * @param n
     * @param type must be "string" or "number" or one of the other built in
     * types. Note that this parameter must be interned! It's not valid to call
     * it with new String("number"). Use null if you don't care which type or
     * expect more than one type for this argument.
     * @param function name of the function that calls this. Only for pretty
     * exceptions.
     * @return variable with index n on the stack, returned as type "type".
     */
    public static Object getArg(LuaCallFrame callFrame, int n, String type,
            String function) {
        Object o = callFrame.get(n - 1);
        if (o == null) {
            throw new RuntimeException("bad argument #" + n + "to '" + function
                    + "' (" + type + " expected, got no value)");
        }
        // type coercion
        if (type == TYPE_STRING) {
            String res = rawTostring(o);
            if (res != null) {
                return res;
            }
        } else if (type == TYPE_NUMBER) {
            Double d = rawTonumber(o);
            if (d != null) {
                return d;
            }
            throw new RuntimeException("bad argument #" + n + " to '" + function
                    + "' (number expected, got string)");
        }
        if (type != null) {
            // type checking
            String isType = type(o);
            if (type != isType) {
                fail("bad argument #" + n + " to '" + function + "' (" + type
                        + " expected, got " + isType + ")");
            }
        }
        return o;
    }

    public static void setmetatable(LuaState state, Object o, LuaTable newMeta, boolean raw) {
        luaAssert(o != null, "Expected table, got nil");

        if (!raw) {
            final Object oldMeta = state.getmetatable(o, false);

            if (oldMeta != null && ((LuaTable) oldMeta).rawget("__metatable") != null) {
                throw new RuntimeException("Can not set metatable of protected object");
            }
        }

        state.setmetatable(o, newMeta);
    }

    public static String type(Object o) {
        if (o == null) {
            return TYPE_NIL;
        }
        if (o instanceof String) {
            return TYPE_STRING;
        }
        if (o instanceof Double) {
            return TYPE_NUMBER;
        }
        if (o instanceof Boolean) {
            return TYPE_BOOLEAN;
        }
        if (o instanceof JavaFunction || o instanceof LuaClosure) {
            return TYPE_FUNCTION;
        }
        if (o instanceof LuaTable) {
            return TYPE_TABLE;
        }
        if (o instanceof LuaThread) {
            return TYPE_THREAD;
        }
        return TYPE_USERDATA;
    }

    private static String tostring_common(Object o) {
        if (o == null) {
            return "nil";
        }
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Double) {
            return rawTostring(o);
        }
        if (o instanceof Boolean) {
            return o.toString();
        }
        if (o instanceof JavaFunction) {
            return "function 0x" + System.identityHashCode(o);
        }
        if (o instanceof LuaClosure) {
            return "function 0x" + System.identityHashCode(o);
        }
        if (o instanceof LuaException) {
            return "exception: " + ((LuaException) o).getMessage();
        }
        if (o instanceof LuaThread) {
            return "thread 0x" + System.identityHashCode(o);
        }
        if (o instanceof Throwable) {
            return o.toString();
        }
        return null;
    }

    public static String tostring_undynamic(Object o) {
        String out = tostring_common(o);
        if (out != null) {
            return out;
        }
        if (o instanceof LuaTable) {
            return "table 0x" + System.identityHashCode(o);
        }
        return "unknown 0x" + System.identityHashCode(o);
    }

    public static Double tonumber(String s) {
        return tonumber(s, 10);
    }

    public static Double tonumber(String s, int radix) {
        if (radix < 2 || radix > 36) {
            throw new RuntimeException("base out of range");
        }

        try {
            if (radix == 10) {
                return Double.valueOf(s);
            } else {
                return (double) Integer.parseInt(s, radix);
            }
        } catch (NumberFormatException e) {
            if ("nan".equalsIgnoreCase(s)) {
                return Double.NaN;
            }
            if ("-nan".equalsIgnoreCase(s)) {
                return -Double.NaN;
            }
            if ("inf".equalsIgnoreCase(s)) {
                return Double.POSITIVE_INFINITY;
            }
            if ("-inf".equalsIgnoreCase(s)) {
                return Double.NEGATIVE_INFINITY;
            }
            return null;
        }
    }

    private static Double toKiloBytes(long freeMemory) {
        return freeMemory / 1024.0;
    }

    public static String rawTostring(Object o) {
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Double) {
            return numberToString((Double) o);
        }
        return null;
    }

    public static Double rawTonumber(Object o) {
        if (o instanceof Double) {
            return (Double) o;
        }
        if (o instanceof String) {
            return tonumber((String) o);
        }
        return null;
    }
}
