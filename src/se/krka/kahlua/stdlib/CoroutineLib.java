/*
 Copyright (c) 2014-2015 Colby Skeggs
 Derived from code that was
 Copyright (c) 2008 Kristofer Karlsson <kristofer.karlsson@gmail.com>

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
import se.krka.kahlua.vm.LuaState;
import se.krka.kahlua.vm.LuaTable;
import se.krka.kahlua.vm.LuaThread;

public enum CoroutineLib implements JavaFunction {

    CREATE {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    LuaClosure c = getFunction(callFrame, nArguments);

                    LuaThread newThread = new LuaThread(callFrame.thread.state, callFrame.thread.environment);
                    newThread.pushNewCallFrame(c, null, 0, 0, -1, true, true);
                    return callFrame.push(newThread);
                }
            },
    RESUME {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    LuaThread t = getCoroutine(callFrame, nArguments);

                    String status = getStatus(t, callFrame.thread);
                    // equals on strings works because they are both constants
                    BaseLib.luaAssert(status == "suspended", "Can not resume thread that is in status: " + status);

                    t.parent = callFrame.thread;

                    LuaCallFrame nextCallFrame = t.currentCallFrame();

                    // Is this the first time the coroutine is resumed?
                    if (nextCallFrame.nArguments == -1) {
                        nextCallFrame.setTop(0);
                    }

                    // Copy arguments
                    for (int i = 1; i < nArguments; i++) {
                        nextCallFrame.push(callFrame.get(i));
                    }

                    // Is this the first time the coroutine is resumed?
                    if (nextCallFrame.nArguments == -1) {
                        nextCallFrame.nArguments = nArguments - 1;
                        nextCallFrame.init();
                    }

                    callFrame.thread.state.currentThread = t;

                    return 0;
                }
            },
    YIELD {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    LuaThread t = callFrame.thread;

                    BaseLib.luaAssert(t.parent != null, "Can not yield outside of a coroutine");

                    LuaCallFrame realCallFrame = t.callFrameStack[t.callFrameTop - 2];
                    yieldHelper(realCallFrame, callFrame, nArguments);
                    return 0;
                }
            },
    STATUS {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    LuaThread t = getCoroutine(callFrame, nArguments);
                    return callFrame.push(getStatus(t, callFrame.thread));
                }
            },
    RUNNING {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    LuaThread t = callFrame.thread;

                    // same behaviour as in original lua,
                    // return nil if it's the root thread
                    return callFrame.push(t.parent == null ? null : t);
                }
            };

    public String getName() {
        return this.name().toLowerCase();
    }

    @Override
    public String toString() {
        return "coroutine." + getName();
    }

    public static void register(LuaState state) {
        LuaTable coroutine = new LuaTable();
        state.getEnvironment().rawset("coroutine", coroutine);
        for (CoroutineLib c : CoroutineLib.values()) {
            coroutine.rawset(c.getName(), c);
        }
    }

    private static String getStatus(LuaThread t, LuaThread caller) {
        if (t.parent == null) {
            return t.isDead() ? "dead" : "suspended";
        } else {
            return caller == t ? "running" : "normal";
        }
    }

    public static void yieldHelper(LuaCallFrame callFrame, LuaCallFrame argsCallFrame, int nArguments) {
        BaseLib.luaAssert(callFrame.insideCoroutine, "Can not yield outside of a coroutine");

        LuaThread t = callFrame.thread;
        LuaThread parent = t.parent;
        t.parent = null;

        LuaCallFrame nextCallFrame = parent.currentCallFrame();

        // Copy arguments
        nextCallFrame.push(Boolean.TRUE);
        for (int i = 0; i < nArguments; i++) {
            Object value = argsCallFrame.get(i);
            nextCallFrame.push(value);
        }

        t.state.currentThread = parent;
    }

    private static LuaClosure getFunction(LuaCallFrame callFrame, int nArguments) {
        BaseLib.luaAssert(nArguments >= 1, "not enough arguments");
        Object o = callFrame.get(0);
        BaseLib.luaAssert(o instanceof LuaClosure, "argument 1 must be a lua function");
        return (LuaClosure) o;
    }

    private static LuaThread getCoroutine(LuaCallFrame callFrame, int nArguments) {
        BaseLib.luaAssert(nArguments >= 1, "not enough arguments");
        Object o = callFrame.get(0);
        BaseLib.luaAssert(o instanceof LuaThread, "argument 1 must be a coroutine");
        return (LuaThread) o;
    }
}
