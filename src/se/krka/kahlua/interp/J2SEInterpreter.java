/*
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
import java.io.IOException;
import java.io.InputStreamReader;

import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm.LuaClosure;
import se.krka.kahlua.vm.LuaState;

public class J2SEInterpreter {

    public static void main(String[] args) throws IOException {
        LuaState state = new LuaState();

        state.getEnvironment().rawset("_getline", new JavaFunction() {
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                try {
                    return callFrame.push(getClosure(callFrame.thread.state, null, input));
                } catch (IOException ex) {
                    throw new RuntimeException("Could not read line", ex);
                }
            }
        });

        state.call(LuaCompiler.loadstring(
                "local getline = _getline\n"
                + "_getline = nil\n"
                + "local function pack(...)\n"
                + "    return {...}, select('#', ...)\n"
                + "end\n"
                + "while true do\n"
                + "    closure = getline()\n"
                + "    if not closure then return end\n"
                + "    local res, n = pack(pcall(closure))\n"
                + "    if res[1] then\n"
                + "        if n > 1 then\n"
                + "            print(unpack(res, 2, n))\n"
                + "        end"
                + "    else\n"
                + "        print('Failed:', res[2], res[3])\n"
                + "    end\n"
                + "end", "execr", state.getEnvironment()));
    }

    private static LuaClosure getClosure(LuaState state, String line, BufferedReader input) throws IOException {
        System.out.print(line == null ? "> " : ">> ");
        System.out.flush();
        String ir = input.readLine();
        if (ir == null) {
            System.out.println();
            System.exit(0);
        }
        if (ir.length() == 0) {
            return null;
        }
        line = line == null ? ir : line + "\n" + ir;
        if (line.charAt(0) == '=') {
            line = "return " + line.substring(1);
        }
        try {
            return LuaCompiler.loadstring(line, "stdin", state.getEnvironment());
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        } catch (RuntimeException e) {
            if (e.getMessage().contains("<eof>")) {
                return getClosure(state, line, input);
            } else {
                System.out.println(e.getMessage());
                return null;
            }
        }
    }
}
