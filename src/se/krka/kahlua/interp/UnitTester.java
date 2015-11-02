/*
 Copyright (c) 2014-2015 Colby Skeggs
 Derived from code that was
 Copyright (c) 2009 Kristofer Karlsson <kristofer.karlsson@gmail.com>

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
package se.krka.kahlua.interp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.LuaState;
import se.krka.kahlua.vm.LuaTable;

public class UnitTester {

    public static void main(String[] args) throws IOException {
        LuaState state = new LuaState();
        LuaCompiler.register(state);

        String run = null;//"table.lua";

        LuaTable tests = new LuaTable();

        for (File f : new File("resources/lua/tests").listFiles()) {
            if (run == null || run.equals(f.getName())) {
                try (BufferedReader input = new BufferedReader(new FileReader(f))) {
                    tests.rawset(f.getName(), LuaCompiler.loadis(input, f.getName(), state.getEnvironment()));
                }
            }
        }

        state.getEnvironment().rawset("tests", tests);

        state.call(LuaCompiler.loadis(UnitTester.class.getResourceAsStream("/lua/unit-tester.lua"), "unit-tester", state.getEnvironment()));

        if (!(Boolean) state.getEnvironment().rawget("success")) {
            System.exit(1);
        }
    }
}
