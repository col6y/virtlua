/**
 * *****************************************************************************
 * Copyright (c) 2007 LuaJ. All rights reserved.
 * Some modifications Copyright (c) 2015 Colby Skeggs.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ****************************************************************************
 */
package org.luaj.kahluafork.compiler;

import java.util.HashMap;
import java.util.Objects;

public class Token {
    /* terminal symbols denoted by reserved words */
    final static int TK_AND = 257;
    final static int TK_BREAK = 258;
    final static int TK_DO = 259;
    final static int TK_ELSE = 260;
    final static int TK_ELSEIF = 261;
    final static int TK_END = 262;
    final static int TK_FALSE = 263;
    final static int TK_FOR = 264;
    final static int TK_FUNCTION = 265;
    final static int TK_IF = 266;
    final static int TK_IN = 267;
    final static int TK_LOCAL = 268;
    final static int TK_NIL = 269;
    final static int TK_NOT = 270;
    final static int TK_OR = 271;
    final static int TK_REPEAT = 272;
    final static int TK_RETURN = 273;
    final static int TK_THEN = 274;
    final static int TK_TRUE = 275;
    final static int TK_UNTIL = 276;
    final static int TK_WHILE = 277;
    final static int TK_CONCAT = 278;
    final static int TK_DOTS = 279;
    final static int TK_EQ = 280;
    final static int TK_GE = 281;
    final static int TK_LE = 282;
    final static int TK_NE = 283;
    final static int TK_NUMBER = 284;
    final static int TK_NAME = 285;
    final static int TK_STRING = 286;
    final static int TK_EOS = 287;
    private final static int FIRST_RESERVED = TK_AND;
    private final static int NUM_RESERVED = TK_WHILE + 1 - FIRST_RESERVED;
    /* ORDER RESERVED */
    private final static String[] luaX_tokens = {
            "and", "break", "do", "else", "elseif",
            "end", "false", "for", "function", "if",
            "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while",
            "..", "...", "==", ">=", "<=", "~=",
            "<number>", "<name>", "<string>", "<eof>"};
    private final static HashMap<String, Integer> RESERVED = new HashMap<>();

    static {
        for (int i = 0; i < NUM_RESERVED; i++) {
            RESERVED.put(luaX_tokens[i], FIRST_RESERVED + i);
        }
    }

    private final int token;
    private final double r;
    private final String ts;

    private Token(int token, double r, String ts) {
        this.token = token;
        this.r = r;
        this.ts = ts;
    }

    static String toString(int token) {
        // TODO: make into a toString()
        if (token < FIRST_RESERVED) {
            return token < ' ' ? "char(" + token + ")" : Character.toString((char) token);
        } else {
            return luaX_tokens[token - FIRST_RESERVED];
        }
    }

    static Token nameOrKeyword(String ts) {
        if (RESERVED.containsKey(ts)) {
            return tok(RESERVED.get(ts));
        } else {
            return name(ts);
        }
    }

    public boolean equals(Object o) {
        return o instanceof Token && ((Token) o).token == token && ((Token) o).r == r && Objects.equals(((Token) o).ts, ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, r, ts);
    }

    public static Token tok(int token) {
        if (token == TK_NAME || token == TK_STRING || token == TK_NUMBER) {
            throw new IllegalArgumentException("Names, strings, and numbers must be created separately.");
        }
        return new Token(token, 0, null);
    }

    private static Token name(String name) {
        return new Token(TK_NAME, 0, name);
    }

    public static Token string(String value) {
        return new Token(TK_STRING, 0, value);
    }

    public static Token number(double r) {
        return new Token(TK_NUMBER, r, null);
    }

    public int getToken() {
        return token;
    }

    public boolean is(int token) {
        return this.token == token;
    }

    public String getString() {
        if (ts == null) {
            throw new IllegalStateException("Not a string or name!");
        }
        return ts;
    }

    public double getNumber() {
        if (!is(TK_NUMBER)) {
            throw new IllegalStateException("Not a number!");
        }
        return r;
    }

    public String toString() {
        switch (token) {
            case TK_NAME:
            case TK_STRING:
                return ts;
            case TK_NUMBER:
                return Double.toString(r);
            default:
                return toString(token);
        }
    }

    public int toUnary() {
        switch (token) {
            case TK_NOT:
                return LexState.OPR_NOT;
            case '-':
                return LexState.OPR_MINUS;
            case '#':
                return LexState.OPR_LEN;
            default:
                return LexState.OPR_NOUNOPR;
        }
    }

    public int toBinary() {
        switch (token) {
            case '+':
                return LexState.OPR_ADD;
            case '-':
                return LexState.OPR_SUB;
            case '*':
                return LexState.OPR_MUL;
            case '/':
                return LexState.OPR_DIV;
            case '%':
                return LexState.OPR_MOD;
            case '^':
                return LexState.OPR_POW;
            case TK_CONCAT:
                return LexState.OPR_CONCAT;
            case TK_NE:
                return LexState.OPR_NE;
            case TK_EQ:
                return LexState.OPR_EQ;
            case '<':
                return LexState.OPR_LT;
            case TK_LE:
                return LexState.OPR_LE;
            case '>':
                return LexState.OPR_GT;
            case TK_GE:
                return LexState.OPR_GE;
            case TK_AND:
                return LexState.OPR_AND;
            case TK_OR:
                return LexState.OPR_OR;
            default:
                return LexState.OPR_NOBINOPR;
        }
    }

    boolean isBlockTerminator() {
        switch (token) {
            case TK_ELSE:
            case TK_ELSEIF:
            case TK_END:
            case TK_UNTIL:
            case TK_EOS:
                return true;
            default:
                return false;
        }
    }
}
