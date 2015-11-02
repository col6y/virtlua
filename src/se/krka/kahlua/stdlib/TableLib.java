/*
 Copyright (c) 2014-2015 Colby Skeggs
 Derived from code that was
 Copyright (c) 2007-2009 Kristofer Karlsson <kristofer.karlsson@gmail.com>
 and Jan Matejek <ja@matejcik.cz>

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
import se.krka.kahlua.vm.LuaState;
import se.krka.kahlua.vm.LuaTable;

public enum TableLib implements JavaFunction {

    CONCAT {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "expected table, got no arguments");
                    LuaTable table = (LuaTable) callFrame.get(0);

                    String separator = "";
                    if (nArguments >= 2 && callFrame.get(1) != null) {
                        separator = BaseLib.rawTostring(callFrame.get(1));
                        if (separator == null) {
                            BaseLib.fail("bad type of argument 2: expected string or number");
                        }
                    }

                    int first = 1;
                    if (nArguments >= 3) {
                        Double firstDouble = BaseLib.rawTonumber(callFrame.get(2));
                        first = firstDouble.intValue();
                    }

                    int last;
                    if (nArguments >= 4) {
                        Double lastDouble = BaseLib.rawTonumber(callFrame.get(3));
                        last = lastDouble.intValue();
                    } else {
                        last = table.len();
                    }

                    StringBuilder buffer = new StringBuilder();
                    for (int i = first; i <= last; i++) {
                        if (i > first) {
                            buffer.append(separator);
                        }

                        Object value = table.rawget((double) i);
                        String valueStr = BaseLib.rawTostring(value);
                        if (valueStr == null) {
                            BaseLib.fail("bad value at index " + i + ": expected string or number");
                        }
                        buffer.append(valueStr);
                    }

                    return callFrame.push(buffer.toString());
                }
            };

    public String getName() {
        return this.name().toLowerCase();
    }

    public static void register(LuaState state) {
        LuaTable table = new LuaTable();
        state.getEnvironment().rawset("table", table);

        for (TableLib f : TableLib.values()) {
            table.rawset(f.getName(), f);
        }
    }

    @Override
    public String toString() {
        return "table." + getName();
    }
}
