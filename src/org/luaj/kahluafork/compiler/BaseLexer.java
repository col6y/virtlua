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

import se.krka.kahlua.vm.LuaException;

import java.io.IOException;
import java.io.Reader;

class BaseLexer {
    static final int MAX_INT = Integer.MAX_VALUE - 2;
    private static final int EOZ = (-1);
    private static final int MAXSRC = 80;
    private final Reader z;  /* input stream */
    private final String source;  /* current source name */
    private final StringBuilder buff = new StringBuilder();
    private Token t = null;  /* current token */
    private Token lookahead;  /* look ahead token */
    private int lastline = 1;  /* line of last token `consumed' */
    private int linenumber = 1;  /* input line counter */
    private int current;  /* current character (charint) */

    public BaseLexer(int firstByte, Reader z, String source) {
        this.z = z;
        this.source = source;
        this.lookahead = Token.tok(Token.TK_EOS); /* no look-ahead token */
        this.current = firstByte; /* read first char */
        skipShebang();
        next();
    }

    private void skipShebang() {
        if (current == '#') {
            while (!currIsNewline() && current != BaseLexer.EOZ) {
                nextChar();
            }
        }
    }

    private boolean isidentifierchar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || current == '_' || isdigit(c);
    }

    private boolean isdigit(int c) {
        return c >= '0' && c <= '9';
    }

    private void nextChar() {
        try {
            current = z.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean currIsNewline() {
        return current == '\n' || current == '\r';
    }

    private void save_and_next() {
        save(current);
        nextChar();
    }

    private void save(int c) {
        buff.append((char) c);
    }

    private String txtToken(Token token) {
        return token.toString();
    }

    private String chunkid(String source) {
        if (source.startsWith("=")) {
            return source.substring(1);
        }
        String end = "";
        if (source.startsWith("@")) {
            source = source.substring(1);
        } else {
            source = "[string \"" + source;
            end = "\"]";
        }
        int n = source.length() + end.length();
        if (n > MAXSRC) {
            source = source.substring(0, MAXSRC - end.length() - 3) + "...";
        }
        return source + end;
    }

    private void lexerror(String msg, Token token) {
        String cid = chunkid(source); // TODO: get source name from source
        String errorMessage;
        if (token != null) {
            errorMessage = cid + ":" + linenumber + ": " + msg + " near '" + txtToken(token) + "'";
        } else {
            errorMessage = cid + ":" + linenumber + ": " + msg;
        }
        throw new LuaException(errorMessage);
    }

    private void inclinenumber() {
        int old = current;
        FuncState._assert(currIsNewline());
        nextChar(); /* skip '\n' or '\r' */

        if (currIsNewline() && current != old) {
            nextChar(); /* skip '\n\r' or '\r\n' */
        }
        if (++linenumber >= MAX_INT) {
            syntaxerror("chunk has too many lines");
        }
    }

    private int skip_sep() {
        int count = 0;
        int s = current;
        FuncState._assert(s == '[' || s == ']');
        save_and_next();
        while (current == '=') {
            save_and_next();
            count++;
        }
        return (current == s) ? count : (-count) - 1;
    }

    private String read_long_string(boolean is_comment, int sep) {
        save_and_next(); /* skip 2nd `[' */

        if (currIsNewline()) /* string starts with a newline? */ {
            inclinenumber(); /* skip it */
        }
        for (boolean endloop = false; !endloop; ) {
            switch (current) {
                case EOZ:
                    lexerror(is_comment ? "unfinished long comment" : "unfinished long string", Token.tok(Token.TK_EOS));
                    break; /* to avoid warnings */
                case '[': {
                    if (skip_sep() == sep) {
                        /* skip 2nd `[' */
                        save(current);
                        nextChar();
                    }
                    break;
                }
                case ']': {
                    if (skip_sep() == sep) {
                         /* skip 2nd `]' */
                        save(current);
                        nextChar();
                        endloop = true;
                    }
                    break;
                }
                case '\n':
                case '\r': {
                    save('\n');
                    inclinenumber();
                    if (is_comment) {
                        buff.setLength(0); /* avoid wasting space */
                    }
                    break;
                }
                default: {
                    if (!is_comment) {
                        save(current);
                    }
                    nextChar();
                }
            }
        }
        return is_comment ? null : buff.substring(2 + sep, buff.length() - (2 + sep));
    }

    private String read_string(int del) {
        save_and_next();
        while (current != del) {
            switch (current) {
                case EOZ:
                    lexerror("unfinished string", Token.tok(Token.TK_EOS));
                    continue; /* to avoid warnings */
                case '\n':
                case '\r':
                    lexerror("unfinished string", Token.string(buff.toString()));
                    continue; /* to avoid warnings */
                case '\\': {
                    int c;
                    nextChar(); /* do not save the `\' */
                    switch (current) {
                        case 'a': /* bell */
                            c = '\u0007';
                            break;
                        case 'b': /* backspace */
                            c = '\b';
                            break;
                        case 'f': /* form feed */
                            c = '\f';
                            break;
                        case 'n': /* newline */
                            c = '\n';
                            break;
                        case 'r': /* carriage return */
                            c = '\r';
                            break;
                        case 't': /* tab */
                            c = '\t';
                            break;
                        case 'v': /* vertical tab */
                            c = '\u000B';
                            break;
                        case '\n': /* go through */
                        case '\r':
                            save('\n');
                            inclinenumber();
                            continue;
                        case EOZ:
                            continue; /* will raise an error next loop */
                        default: {
                            if (!isdigit(current)) {
                                save_and_next(); /* handles \\, \", \', and \? */
                            } else { /* \xxx */
                                c = current - '0';
                                nextChar();
                                if (isdigit(current)) {
                                    c = 10 * c + (current - '0');
                                    nextChar();
                                    if (isdigit(current)) {
                                        c = 10 * c + (current - '0');
                                        nextChar();
                                    }
                                }
                                save(c);
                            }
                            continue;
                        }
                    }
                    save(c);
                    nextChar();
                    continue;
                }
                default:
                    save_and_next();
            }
        }
        save_and_next(); /* skip delimiter */

        return buff.substring(1, 1 + buff.length() - 2);
    }

    private boolean check_next(String set) {
        if (set.indexOf(current) < 0) {
            return false;
        }
        save_and_next();
        return true;
    }

    private double read_numeral() {
        FuncState._assert(isdigit(current));
        do {
            save_and_next();
        } while (isdigit(current) || current == '.');
        if (check_next("Ee")) /* `E'? */ {
            check_next("+-"); /* optional exponent sign */
        }
        while (isidentifierchar(current)) {
            save_and_next();
        }
        save('\0');

        String str = buff.toString();
        double d;
        str = str.trim(); // TODO: get rid of this
        if (str.startsWith("0x")) {
            d = Long.parseLong(str.substring(2), 16);
        } else {
            d = Double.parseDouble(str);
        }
        return d;
    }

    private Token llex() {
        buff.setLength(0);
        while (true) {
            switch (current) {
                case '\n':
                case '\r': {
                    inclinenumber();
                    continue;
                }
                case '-': {
                    nextChar();
                    if (current != '-') {
                        return Token.tok('-');
                    }
                    /* else is a comment */
                    nextChar();
                    if (current == '[') {
                        int sep = skip_sep();
                        buff.setLength(0); /* `skip_sep' may dirty the buffer */
                        if (sep >= 0) {
                            read_long_string(true, sep); /* long comment */
                            buff.setLength(0);
                            continue;
                        }
                    }
                    /* else short comment */
                    while (!currIsNewline() && current != EOZ) {
                        nextChar();
                    }
                    continue;
                }
                case '[': {
                    int sep = skip_sep();
                    if (sep >= 0) {
                        return Token.string(read_long_string(false, sep));
                    } else if (sep == -1) {
                        return Token.tok('[');
                    } else {
                        lexerror("invalid long string delimiter", Token.string(buff.toString()));
                    }
                }
                case '=': {
                    nextChar();
                    if (current != '=') {
                        return Token.tok('=');
                    } else {
                        nextChar();
                        return Token.tok(Token.TK_EQ);
                    }
                }
                case '<': {
                    nextChar();
                    if (current != '=') {
                        return Token.tok('<');
                    } else {
                        nextChar();
                        return Token.tok(Token.TK_LE);
                    }
                }
                case '>': {
                    nextChar();
                    if (current != '=') {
                        return Token.tok('>');
                    } else {
                        nextChar();
                        return Token.tok(Token.TK_GE);
                    }
                }
                case '~': {
                    nextChar();
                    if (current != '=') {
                        return Token.tok('~');
                    } else {
                        nextChar();
                        return Token.tok(Token.TK_NE);
                    }
                }
                case '"':
                case '\'': {
                    return Token.string(read_string(current));
                }
                case '.': {
                    save_and_next();
                    if (check_next(".")) {
                        if (check_next(".")) {
                            return Token.tok(Token.TK_DOTS); /* ... */
                        } else {
                            return Token.tok(Token.TK_CONCAT); /* .. */
                        }
                    } else if (!isdigit(current)) {
                        return Token.tok('.');
                    } else {
                        return Token.number(read_numeral());
                    }
                }
                case EOZ: {
                    return Token.tok(Token.TK_EOS);
                }
                default: {
                    if (current <= ' ') {
                        FuncState._assert(!currIsNewline());
                        nextChar();
                    } else if (isdigit(current)) {
                        return Token.number(read_numeral());
                    } else if (isidentifierchar(current)) {
                        /* identifier or reserved word */
                        String ts;
                        do {
                            save_and_next();
                        } while (isidentifierchar(current));
                        ts = buff.toString();
                        return Token.nameOrKeyword(ts);
                    } else {
                        int c = current;
                        nextChar();
                        return Token.tok(c); /* single-char tokens (+ - / ...) */
                    }
                }
            }
        }
    }

    private void lookahead() {
        FuncState._assert(lookahead.is(Token.TK_EOS));
        lookahead = llex();
    }

    private void error_expected(int token) {
        syntaxerror("'" + Token.toString(token) + "' expected");
    }

    void next() {
        lastline = linenumber;
        if (!lookahead.is(Token.TK_EOS)) { /* is there a look-ahead token? */
            t = lookahead;
            lookahead = Token.tok(Token.TK_EOS); /* and discharge it */
        } else {
            t = llex(); /* read next token */
        }
    }

    boolean test(int c) {
        return t.is(c);
    }

    boolean testnext(int c) {
        if (test(c)) {
            next();
            return true;
        } else {
            return false;
        }
    }

    void check(int c) {
        if (!t.is(c)) {
            error_expected(c);
        }
    }

    void checknext(int c) {
        check(c);
        next();
    }

    void check_condition(boolean c, String msg) {
        if (!c) {
            syntaxerror(msg);
        }
    }

    void check_match(int what, int who, int where) {
        if (!testnext(what)) {
            if (where == linenumber) {
                error_expected(what);
            } else {
                syntaxerror("'" + Token.toString(what) + "' expected " + "(to close '" + Token.toString(who) + "' at line " + where + ")");
            }
        }
    }

    String str_checkname() {
        check(Token.TK_NAME);
        return t.getString();
    }

    String str_checkstring() {
        check(Token.TK_STRING);
        return t.getString();
    }

    public double checknumber() {
        check(Token.TK_NUMBER);
        return t.getNumber();
    }

    String str_checkname_next() {
        String name = str_checkname();
        next();
        return name;
    }

    public int switchToken() {
        return t.getToken();
    }

    public boolean isLookahead(char c) {
        if (lookahead.is(Token.TK_EOS)) {
            lookahead();
        }
        return lookahead.is(c);
    }

    public int getLine() {
        return linenumber;
    }

    public int getLastLine() {
        return lastline;
    }

    public Token getToken() {
        return t;
    }

    void syntaxerror(String msg) {
        lexerror(msg, t);
    }
}
