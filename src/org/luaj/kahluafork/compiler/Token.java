package org.luaj.kahluafork.compiler;

import java.util.Objects;

public class Token {
    private final int token;
    private final double r;
    private final String ts;

    private Token(int token, double r, String ts) {
        this.token = token;
        this.r = r;
        this.ts = ts;
    }

    public boolean equals(Object o) {
        return o instanceof Token && ((Token) o).token == token && ((Token) o).r == r && Objects.equals(((Token) o).ts, ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, r, ts);
    }

    public static Token tok(int token) {
        return new Token(token, 0, null);
    }

    public static Token name(String name) {
        return new Token(BaseLexer.TK_NAME, 0, name);
    }

    public static Token string(String value) {
        return new Token(BaseLexer.TK_STRING, 0, value);
    }

    public static Token number(double r) {
        return new Token(BaseLexer.TK_NUMBER, r, null);
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
        if (!is(BaseLexer.TK_NUMBER)) {
            throw new IllegalStateException("Not a number!");
        }
        return r;
    }
}
