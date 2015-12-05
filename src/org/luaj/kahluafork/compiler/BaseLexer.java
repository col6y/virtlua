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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

class BaseLexer {
    static final int MAX_INT = Integer.MAX_VALUE - 2;
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
    private static final int EOZ = (-1);
    private static final int UCHAR_MAX = 255; // TODO, convert to unicode CHAR_MAX?
    private static final int MAXSRC = 80;
    private final static int FIRST_RESERVED = TK_AND;
    private final static int NUM_RESERVED = TK_WHILE + 1 - FIRST_RESERVED;
    /* ORDER RESERVED */
    private final static String[] luaX_tokens = {
            "and", "break", "do", "else", "elseif",
            "end", "false", "for", "function", "if",
            "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while",
            "..", "...", "==", ">=", "<=", "~=",
            "<number>", "<name>", "<string>", "<eof>",};
    private final static HashMap<String, Integer> RESERVED = new HashMap<>();

    static {
        for (int i = 0; i < NUM_RESERVED; i++) {
            RESERVED.put(luaX_tokens[i], FIRST_RESERVED + i);
        }
    }

    final Token t = new Token();  /* current token */
    final Token lookahead = new Token();  /* look ahead token */
    private final Reader z;  /* input stream */
    private final String source;  /* current source name */
    private final HashMap<String, String> strings = new HashMap<>();
    int lastline = 1;  /* line of last token `consumed' */
    int linenumber = 1;  /* input line counter */
    private int current;  /* current character (charint) */
    private byte[] buff = new byte[32];  /* buffer for tokens */
    private int nbuff = 0; /* length of buffer */
    public BaseLexer(int firstByte, Reader z, String source) {
        this.z = z;
        this.source = source;
        this.lookahead.token = TK_EOS; /* no look-ahead token */
        this.current = firstByte; /* read first char */
        skipShebang();
        next();
    }

    private static String LUA_QS(String s) {
        return "'" + s + "'";
    }

    static String LUA_QL(Object o) {
        return LUA_QS(String.valueOf(o));
    }

    private static boolean iscntrl(int token) {
        return token < ' ';
    }

    private void skipShebang() {
        if (current == '#') {
            while (!currIsNewline() && current != BaseLexer.EOZ) {
                nextChar();
            }
        }
    }

    private boolean isalnum(int c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c == '_');
    }

    private boolean isalpha(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z');
    }

    private boolean isdigit(int c) {
        return (c >= '0' && c <= '9');
    }

    private void nextChar() {
        try {
            current = z.read();
        } catch (IOException e) {
            e.printStackTrace();
            current = EOZ;
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
        if (buff == null || nbuff + 1 > buff.length) {
            buff = FuncState.realloc(buff, nbuff * 2 + 1);
        }
        buff[nbuff++] = (byte) c;
    }

    private String token2str(int token) {
        if (token < FIRST_RESERVED) {
            return iscntrl(token)
                    ? "char(" + token + ")"
                    : String.valueOf((char) token);
        } else {
            return luaX_tokens[token - FIRST_RESERVED];
        }
    }

    private String txtToken(int token) {
        switch (token) {
            case TK_NAME:
            case TK_STRING:
            case TK_NUMBER:
                return new String(buff, 0, nbuff);
            default:
                return token2str(token);
        }
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

    void lexerror(String msg, int token) {
        String cid = chunkid(source); // TODO: get source name from source
        String errorMessage;
        if (token != 0) {
            errorMessage = cid + ":" + linenumber + ": " + msg + " near `" + txtToken(token) + "`";
        } else {
            errorMessage = cid + ":" + linenumber + ": " + msg;
        }
        throw new LuaException(errorMessage);
    }

    void syntaxerror(String msg) {
        lexerror(msg, t.token);
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

    private void read_long_string(Token token, int sep) {
        save_and_next(); /* skip 2nd `[' */

        if (currIsNewline()) /* string starts with a newline? */ {
            inclinenumber(); /* skip it */
        }
        for (boolean endloop = false; !endloop; ) {
            switch (current) {
                case EOZ:
                    lexerror((token != null) ? "unfinished long string"
                            : "unfinished long comment", TK_EOS);
                    break; /* to avoid warnings */
                case '[': {
                    if (skip_sep() == sep) {
                        save_and_next(); /* skip 2nd `[' */
                    }
                    break;
                }
                case ']': {
                    if (skip_sep() == sep) {
                        save_and_next(); /* skip 2nd `]' */
                        endloop = true;
                    }
                    break;
                }
                case '\n':
                case '\r': {
                    save('\n');
                    inclinenumber();
                    if (token == null) {
                        nbuff = 0; /* avoid wasting space */
                    }
                    break;
                }
                default: {
                    if (token != null) {
                        save_and_next();
                    } else {
                        nextChar();
                    }
                }
            }
        }
        if (token != null) {
            token.ts = newstring(buff, 2 + sep, nbuff - 2 * (2 + sep));
        }
    }

    String newstring(String s) {
        return newTString(s);
    }

    private String newstring(byte[] chars, int offset, int len) {
        try {
            String s = new String(chars, offset, len, "UTF-8");
            return newTString(s);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private String newTString(String s) {
        String t = strings.get(s);
        if (t == null) {
            t = s;
            strings.put(t, t);
        }
        return t;
    }

    private void read_string(int del, Token token) {
        save_and_next();
        while (current != del) {
            switch (current) {
                case EOZ:
                    lexerror("unfinished string", TK_EOS);
                    continue; /* to avoid warnings */
                case '\n':
                case '\r':
                    lexerror("unfinished string", TK_STRING);
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
                                int i = 0;
                                c = 0;
                                do {
                                    c = 10 * c + (current - '0');
                                    nextChar();
                                } while (++i < 3 && isdigit(current));
                                if (c > UCHAR_MAX) {
                                    lexerror("escape sequence too large", TK_STRING);
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

        token.ts = newstring(buff, 1, nbuff - 2);
    }

    private boolean check_next(String set) {
        if (set.indexOf(current) < 0) {
            return false;
        }
        save_and_next();
        return true;
    }

    private void read_numeral(Token token) {
        FuncState._assert(isdigit(current));
        do {
            save_and_next();
        } while (isdigit(current) || current == '.');
        if (check_next("Ee")) /* `E'? */ {
            check_next("+-"); /* optional exponent sign */
        }
        while (isalnum(current) || current == '_') {
            save_and_next();
        }
        save('\0');

        str2d(new String(buff, 0, nbuff), token);
    }

    private void str2d(String str, BaseLexer.Token token) {
        double d;
        str = str.trim(); // TODO: get rid of this
        if (str.startsWith("0x")) {
            d = Long.parseLong(str.substring(2), 16);
        } else {
            d = Double.parseDouble(str);
        }
        token.r = d;
    }

    private int llex(Token token) {
        nbuff = 0;
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
                        return '-';
                    }
                    /* else is a comment */
                    nextChar();
                    if (current == '[') {
                        int sep = skip_sep();
                        nbuff = 0; /* `skip_sep' may dirty the buffer */
                        if (sep >= 0) {
                            read_long_string(null, sep); /* long comment */
                            nbuff = 0;
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
                        read_long_string(token, sep);
                        return TK_STRING;
                    } else if (sep == -1) {
                        return '[';
                    } else {
                        lexerror("invalid long string delimiter", TK_STRING);
                    }
                }
                case '=': {
                    nextChar();
                    if (current != '=') {
                        return '=';
                    } else {
                        nextChar();
                        return TK_EQ;
                    }
                }
                case '<': {
                    nextChar();
                    if (current != '=') {
                        return '<';
                    } else {
                        nextChar();
                        return TK_LE;
                    }
                }
                case '>': {
                    nextChar();
                    if (current != '=') {
                        return '>';
                    } else {
                        nextChar();
                        return TK_GE;
                    }
                }
                case '~': {
                    nextChar();
                    if (current != '=') {
                        return '~';
                    } else {
                        nextChar();
                        return TK_NE;
                    }
                }
                case '"':
                case '\'': {
                    read_string(current, token);
                    return TK_STRING;
                }
                case '.': {
                    save_and_next();
                    if (check_next(".")) {
                        if (check_next(".")) {
                            return TK_DOTS; /* ... */
                        } else {
                            return TK_CONCAT; /* .. */
                        }
                    } else if (!isdigit(current)) {
                        return '.';
                    } else {
                        read_numeral(token);
                        return TK_NUMBER;
                    }
                }
                case EOZ: {
                    return TK_EOS;
                }
                default: {
                    if (current <= ' ') {
                        FuncState._assert(!currIsNewline());
                        nextChar();
                    } else if (isdigit(current)) {
                        read_numeral(token);
                        return TK_NUMBER;
                    } else if (isalpha(current) || current == '_') {
                        /* identifier or reserved word */
                        String ts;
                        do {
                            save_and_next();
                        } while (isalnum(current) || current == '_');
                        ts = newstring(buff, 0, nbuff);
                        if (RESERVED.containsKey(ts)) {
                            return RESERVED.get(ts);
                        } else {
                            token.ts = ts;
                            return TK_NAME;
                        }
                    } else {
                        int c = current;
                        nextChar();
                        return c; /* single-char tokens (+ - / ...) */
                    }
                }
            }
        }
    }

    void next() {
        lastline = linenumber;
        if (lookahead.token != TK_EOS) { /* is there a look-ahead token? */
            t.set(lookahead); /* use this one */
            lookahead.token = TK_EOS; /* and discharge it */
        } else {
            t.token = llex(t); /* read next token */
        }
    }

    void lookahead() {
        FuncState._assert(lookahead.token == TK_EOS);
        lookahead.token = llex(lookahead);
    }

    /*
     * * prototypes for recursive non-terminal functions
     */
    private void error_expected(int token) {
        syntaxerror(LUA_QS(token2str(token)) + " expected");
    }

    boolean testnext(int c) {
        if (t.token == c) {
            next();
            return true;
        } else {
            return false;
        }
    }

    void check(int c) {
        if (t.token != c) {
            error_expected(c);
        }
    }

    void checknext(int c) {
        check(c);
        next();
    }

    void check_condition(boolean c, String msg) {
        if (!(c)) {
            syntaxerror(msg);
        }
    }

    void check_match(int what, int who, int where) {
        if (!testnext(what)) {
            if (where == linenumber) {
                error_expected(what);
            } else {
                syntaxerror(LUA_QS(token2str(what))
                        + " expected " + "(to close " + LUA_QS(token2str(who))
                        + " at line " + where + ")");
            }
        }
    }

    String str_checkname() {
        String ts;
        check(TK_NAME);
        ts = t.ts;
        next();
        return ts;
    }

    public static class Token {
        int token;

        /* semantics information */
        double r;
        String ts;

        public void set(Token other) {
            this.token = other.token;
            this.r = other.r;
            this.ts = other.ts;
        }
    }
}
