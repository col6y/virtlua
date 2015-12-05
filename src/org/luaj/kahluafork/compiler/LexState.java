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

import java.io.Reader;

import org.luaj.kahluafork.compiler.FuncState.BlockCnt;
import se.krka.kahlua.vm.LuaPrototype;

public class LexState {

    /*
     ** Marks the end of a patch list. It is an invalid value both as an absolute
     ** address, and as a list link (would link an element to itself).
     */
    static final int NO_JUMP = (-1);
    /*
     ** grep "ORDER OPR" if you change these enums
     */
    static final int OPR_ADD = 0;
    static final int OPR_SUB = 1;
    static final int OPR_MUL = 2;
    static final int OPR_DIV = 3;
    static final int OPR_MOD = 4;
    static final int OPR_POW = 5;
    static final int OPR_CONCAT = 6;
    static final int OPR_NE = 7;
    static final int OPR_EQ = 8;
    static final int OPR_LT = 9;
    static final int OPR_LE = 10;
    static final int OPR_GT = 11;
    static final int OPR_GE = 12;
    static final int OPR_AND = 13;
    static final int OPR_OR = 14;
    static final int OPR_MINUS = 0;
    static final int OPR_NOT = 1;
    static final int OPR_LEN = 2;
    /* exp kind */
    static final int VVOID = 0, /* no value */
            VNIL = 1,
            VTRUE = 2,
            VFALSE = 3,
            VK = 4, /* info = index of constant in `k' */
            VKNUM = 5, /* nval = numerical value */
            VLOCAL = 6, /* info = local register */
            VUPVAL = 7, /* info = index of upvalue in `upvalues' */
            VGLOBAL = 8, /* info = index of table, aux = index of global name in `k' */
            VINDEXED = 9, /* info = table register, aux = index register (or `k') */
            VJMP = 10, /* info = instruction pc */
            VRELOCABLE = 11, /* info = instruction pc */
            VNONRELOC = 12, /* info = result register */
            VCALL = 13, /* info = instruction pc */
            VVARARG = 14;	/* info = instruction pc */
    private static final String RESERVED_LOCAL_VAR_FOR_CONTROL = "(for control)";
    private static final String RESERVED_LOCAL_VAR_FOR_STATE = "(for state)";
    private static final String RESERVED_LOCAL_VAR_FOR_GENERATOR = "(for generator)";
    private static final String RESERVED_LOCAL_VAR_FOR_STEP = "(for step)";
    private static final String RESERVED_LOCAL_VAR_FOR_LIMIT = "(for limit)";
    private static final String RESERVED_LOCAL_VAR_FOR_INDEX = "(for index)";
    private static final int LUAI_MAXCCALLS = 200;
    private static final int OPR_NOBINOPR = 15;
    private static final int OPR_NOUNOPR = 3;
    private static final int[] priorityLeft = {
            6, 6, 7, 7, 7, /* `+' `-' `/' `%' */
            10, 5, /* power and concat (right associative) */
            3, 3, /* equality and inequality */
            3, 3, 3, 3, /* order */
            2, 1, /* logical (and/or) */};
    private static final int[] priorityRight = { /* ORDER OPR */
            6, 6, 7, 7, 7, /* `+' `-' `/' `%' */
            9, 4, /* power and concat (right associative) */
            3, 3, /* equality and inequality */
            3, 3, 3, 3, /* order */
            2, 1 /* logical (and/or) */};
    private static final int UNARY_PRIORITY = 8;  /* priority for unary operators */

    final BaseLexer baseLexer;

    private int nCcalls;
    private FuncState fs;  /* `FuncState' is private to the parser */

    private LexState(int firstByte, Reader stream, String name) {
        baseLexer = new BaseLexer(firstByte, stream, name);
    }

    public static LuaPrototype compile(int firstByte, Reader z, String name) {
        LexState lexstate = new LexState(firstByte, z, name);
        lexstate.fs = new FuncState("@" + name, lexstate.fs, lexstate, 0);
        FuncState funcstate = lexstate.fs;
        /* main func. is always vararg */
        funcstate.isVararg = FuncState.VARARG_ISVARARG;
        lexstate.compile();
        FuncState._assert(funcstate.prev == null);
        FuncState._assert(funcstate.f.numUpvalues == 0);
        return funcstate.f;
    }

    /*
     ** converts an integer to a "floating point byte", represented as
     ** (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if
     ** eeeee != 0 and (xxx) otherwise.
     */
    private static int luaO_int2fb(int x) {
        int e = 0;  /* expoent */

        while (x >= 16) {
            x = (x + 1) >> 1;
            e++;
        }
        if (x < 8) {
            return x;
        } else {
            return ((e + 1) << 3) | (x - 8);
        }
    }

    private static boolean hasmultret(int k) {
        return ((k) == VCALL || (k) == VVARARG);
    }

    private void compile() {
        chunk();
        baseLexer.check(BaseLexer.TK_EOS);
        close_func();
        FuncState._assert(fs == null);
    }

    void lexerrorNotoken(String msg) {
        baseLexer.lexerror(msg, 0);
    }

    void syntaxerror(String msg) {
        baseLexer.syntaxerror(msg);
    }

    private void checkname(expdesc e) {
        codestring(e, baseLexer.str_checkname());
    }

    private void codestring(expdesc e, String s) {
        e.init(VK, fs.stringK(s));
    }

    private int registerlocalvar(String varname) {
        FuncState fs = this.fs;
        if (fs.locvars == null || fs.nlocvars + 1 > fs.locvars.length) {
            fs.locvars = FuncState.realloc(fs.locvars, fs.nlocvars * 2 + 1);
        }
        fs.locvars[fs.nlocvars] = varname;
        return fs.nlocvars++;
    }

    private void new_localvarliteral(String v, int n) {
        String ts = baseLexer.newstring(v);
        new_localvar(ts, n);
    }

    private void new_localvar(String name, int n) {
        FuncState fs = this.fs;
        fs.checklimit(fs.nactvar + n + 1, FuncState.LUAI_MAXVARS, "local variables");
        fs.actvar[fs.nactvar + n] = (short) registerlocalvar(name);
    }

    private void adjustlocalvars(int nvars) {
        FuncState fs = this.fs;
        fs.nactvar = (fs.nactvar + nvars);
    }

    void removevars(int tolevel) {
        FuncState fs = this.fs;
        fs.nactvar = tolevel;
    }

    private void singlevar(expdesc var) {
        String varname = baseLexer.str_checkname();
        var.lastname = varname;
        FuncState fs = this.fs;
        if (fs.singlevaraux(varname, var, 1) == VGLOBAL) {
            var.info = fs.stringK(varname); /* info points to global name */
        }
    }

    private void adjust_assign(int nvars, int nexps, expdesc e) {
        FuncState fs = this.fs;
        int extra = nvars - nexps;
        if (hasmultret(e.k)) {
            /* includes call itself */
            extra++;
            if (extra < 0) {
                extra = 0;
            }
            /* last exp. provides the difference */
            fs.setreturns(e, extra);
            if (extra > 1) {
                fs.reserveregs(extra - 1);
            }
        } else {
            /* close last expression */
            if (e.k != VVOID) {
                fs.exp2nextreg(e);
            }
            if (extra > 0) {
                int reg = fs.freereg;
                fs.reserveregs(extra);
                fs.nil(reg, extra);
            }
        }
    }

    private void enterlevel() {
        if (++nCcalls > LUAI_MAXCCALLS) {
            baseLexer.lexerror("chunk has too many syntax levels", 0);
        }
    }

    private void leavelevel() {
        nCcalls--;
    }

    private void pushclosure(FuncState func, expdesc v) {
        FuncState fs = this.fs;
        LuaPrototype f = fs.f;
        if (f.prototypes == null || fs.np + 1 > f.prototypes.length) {
            f.prototypes = FuncState.realloc(f.prototypes, fs.np * 2 + 1);
        }
        f.prototypes[fs.np++] = func.f;
        v.init(VRELOCABLE, fs.codeABx(FuncState.OP_CLOSURE, 0, fs.np - 1));
        for (int i = 0; i < func.f.numUpvalues; i++) {
            int o = (func.upvalues_k[i] == VLOCAL) ? FuncState.OP_MOVE
                    : FuncState.OP_GETUPVAL;
            fs.codeABC(o, 0, func.upvalues_info[i], 0);
        }
    }

    private void close_func() {
        FuncState fs = this.fs;
        LuaPrototype f = fs.f;
        f.isVararg = fs.isVararg != 0;

        this.removevars(0);
        fs.ret(0, 0); /* final return */

        f.code = FuncState.realloc(f.code, fs.pc);
        f.lines = FuncState.realloc(f.lines, fs.pc);
        f.constants = FuncState.realloc(f.constants, fs.nk);
        f.prototypes = FuncState.realloc(f.prototypes, fs.np);
        fs.locvars = FuncState.realloc(fs.locvars, fs.nlocvars);
        fs.upvalues = FuncState.realloc(fs.upvalues, f.numUpvalues);
        FuncState._assert(fs.bl == null);
        this.fs = fs.prev;
    }

    /*============================================================*/
    /* GRAMMAR RULES */
    /*============================================================*/
    private void field(expdesc v) {
        /* field -> ['.' | ':'] NAME */
        FuncState fs = this.fs;
        expdesc key = new expdesc();
        fs.exp2anyreg(v);
        baseLexer.next(); /* skip the dot or colon */

        baseLexer.check(BaseLexer.TK_NAME);
        v.lastname = baseLexer.t.ts;
        checkname(key);
        fs.indexed(v, key);
    }

    private void yindex(expdesc v) {
        /* index -> '[' expr ']' */
        baseLexer.next(); /* skip the '[' */

        this.expr(v);
        this.fs.exp2val(v);
        baseLexer.checknext(']');
    }

    private void recfield(ConsControl cc) {
        /* recfield -> (NAME | `['exp1`]') = exp1 */
        FuncState fs = this.fs;
        int reg = this.fs.freereg;
        expdesc key = new expdesc();
        expdesc val = new expdesc();
        int rkkey;
        if (this.baseLexer.t.token == BaseLexer.TK_NAME) {
            fs.checklimit(cc.nh, BaseLexer.MAX_INT, "items in a constructor");
            checkname(key);
        } else /* this.t.token == '[' */ {
            this.yindex(key);
        }
        cc.nh++;
        baseLexer.checknext('=');
        rkkey = fs.exp2RK(key);
        this.expr(val);
        fs.codeABC(FuncState.OP_SETTABLE, cc.t.info, rkkey, fs.exp2RK(val));
        fs.freereg = reg; /* free registers */
    }

    private void listfield(ConsControl cc) {
        this.expr(cc.v);
        fs.checklimit(cc.na, BaseLexer.MAX_INT, "items in a constructor");
        cc.na++;
        cc.tostore++;
    }

    private void constructor(expdesc t) {
        /* constructor -> ?? */
        FuncState fs = this.fs;
        int line = this.baseLexer.linenumber;
        int pc = fs.codeABC(FuncState.OP_NEWTABLE, 0, 0, 0);
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        t.init(VRELOCABLE, pc);
        cc.v.init(VVOID, 0); /* no value (yet) */

        fs.exp2nextreg(t); /* fix it at stack top (for gc) */

        baseLexer.checknext('{');
        do {
            FuncState._assert(cc.v.k == VVOID || cc.tostore > 0);
            if (this.baseLexer.t.token == '}') {
                break;
            }
            fs.closelistfield(cc);
            switch (this.baseLexer.t.token) {
                case BaseLexer.TK_NAME: { /* may be listfields or recfields */
                    baseLexer.lookahead();
                    if (this.baseLexer.lookahead.token != '=') /* expression? */ {
                        this.listfield(cc);
                    } else {
                        this.recfield(cc);
                    }
                    break;
                }
                case '[': { /* constructor_item -> recfield */
                    this.recfield(cc);
                    break;
                }
                default: { /* constructor_part -> listfield */
                    this.listfield(cc);
                    break;
                }
            }
        } while (baseLexer.testnext(',') || baseLexer.testnext(';'));
        baseLexer.check_match('}', '{', line);
        fs.lastlistfield(cc);
        InstructionPtr i = new InstructionPtr(fs.f.code, pc);
        FuncState.SETARG_B(i, luaO_int2fb(cc.na)); /* set initial array size */
        FuncState.SETARG_C(i, luaO_int2fb(cc.nh));  /* set initial table size */
    }

    private void parlist() {
        /* parlist -> [ param { `,' param } ] */
        FuncState fs = this.fs;
        LuaPrototype f = fs.f;
        int nparams = 0;
        fs.isVararg = 0;
        if (this.baseLexer.t.token != ')') {  /* is `parlist' not empty? */
            do {
                switch (this.baseLexer.t.token) {
                    case BaseLexer.TK_NAME: {  /* param . NAME */
                        this.new_localvar(baseLexer.str_checkname(), nparams++);
                        break;
                    }
                    case BaseLexer.TK_DOTS: {  /* param . `...' */
                        baseLexer.next();
                        fs.isVararg |= FuncState.VARARG_ISVARARG;
                        break;
                    }
                    default:
                        baseLexer.syntaxerror("<name> or '...' expected");
                }
            } while ((fs.isVararg == 0) && baseLexer.testnext(','));
        }
        this.adjustlocalvars(nparams);
        f.numParams = (fs.nactvar - (fs.isVararg & FuncState.VARARG_HASARG));
        fs.reserveregs(fs.nactvar);  /* reserve register for parameters */
    }

    private void body(expdesc e, boolean needself, int line, String fname) {
        /* body -> `(' parlist `)' chunk END */
        String func_name = fname == null ? this.fs.f.name : this.fs.f.name + "/" + fname;
        fs = new FuncState(func_name, fs, this, line);
        FuncState new_fs = fs;
        baseLexer.checknext('(');
        if (needself) {
            new_localvarliteral("self", 0);
            adjustlocalvars(1);
        }
        this.parlist();
        baseLexer.checknext(')');
        this.chunk();
        baseLexer.check_match(BaseLexer.TK_END, BaseLexer.TK_FUNCTION, line);
        this.close_func();
        this.pushclosure(new_fs, e);
    }

    private int explist1(expdesc v) {
        /* explist1 -> expr { `,' expr } */
        int n = 1; /* at least one expression */
        this.expr(v);
        while (baseLexer.testnext(',')) {
            fs.exp2nextreg(v);
            this.expr(v);
            n++;
        }
        return n;
    }

    private void funcargs(expdesc f) {
        FuncState fs = this.fs;
        expdesc args = new expdesc();
        int base, nparams;
        int line = this.baseLexer.linenumber;
        switch (this.baseLexer.t.token) {
            case '(': { /* funcargs -> `(' [ explist1 ] `)' */
                if (line != this.baseLexer.lastline) {
                    baseLexer.syntaxerror("ambiguous syntax (function call x new statement)");
                }
                baseLexer.next();
                if (this.baseLexer.t.token == ')') /* arg list is empty? */ {
                    args.k = VVOID;
                } else {
                    this.explist1(args);
                    fs.setmultret(args);
                }
                baseLexer.check_match(')', '(', line);
                break;
            }
            case '{': { /* funcargs -> constructor */
                this.constructor(args);
                break;
            }
            case BaseLexer.TK_STRING: { /* funcargs -> STRING */
                this.codestring(args, this.baseLexer.t.ts);
                baseLexer.next(); /* must use `seminfo' before `next' */
                break;
            }
            default: {
                baseLexer.syntaxerror("function arguments expected");
                return;
            }
        }
        FuncState._assert(f.k == VNONRELOC);
        base = f.info; /* base register for call */

        if (hasmultret(args.k)) {
            nparams = FuncState.LUA_MULTRET; /* open call */
        } else {
            if (args.k != VVOID) {
                fs.exp2nextreg(args); /* close last argument */
            }
            nparams = fs.freereg - (base + 1);
        }
        f.init(VCALL, fs.codeABC(FuncState.OP_CALL, base, nparams + 1, 2));
        fs.fixline(line);
        fs.freereg = base + 1;  /* call remove function and arguments and leaves
         * (unless changed) one result */
    }


    /*
     ** {======================================================================
     ** Expression parsing
     ** =======================================================================
     */
    private void prefixexp(expdesc v) {
        /* prefixexp -> NAME | '(' expr ')' */
        switch (this.baseLexer.t.token) {
            case '(': {
                int line = this.baseLexer.linenumber;
                baseLexer.next();
                this.expr(v);
                baseLexer.check_match(')', '(', line);
                fs.dischargevars(v);
                break;
            }
            case BaseLexer.TK_NAME: {
                this.singlevar(v);
                break;
            }
            default: {
                baseLexer.syntaxerror("unexpected symbol");
                break;
            }
        }
    }

    private void primaryexp(expdesc v) {
        /*
         * primaryexp -> prefixexp { `.' NAME | `[' exp `]' | `:' NAME funcargs |
         * funcargs }
         */
        FuncState fs = this.fs;
        this.prefixexp(v);
        for (; ; ) {
            switch (this.baseLexer.t.token) {
                case '.': { /* field */
                    this.field(v);
                    break;
                }
                case '[': { /* `[' exp1 `]' */
                    expdesc key = new expdesc();
                    fs.exp2anyreg(v);
                    this.yindex(key);
                    fs.indexed(v, key);
                    break;
                }
                case ':': { /* `:' NAME funcargs */
                    expdesc key = new expdesc();
                    baseLexer.next();
                    checkname(key);
                    fs.self(v, key);
                    this.funcargs(v);
                    break;
                }
                case '(':
                case BaseLexer.TK_STRING:
                case '{': { /* funcargs */
                    fs.exp2nextreg(v);
                    this.funcargs(v);
                    break;
                }
                default:
                    return;
            }
        }
    }

    private void simpleexp(expdesc v) {
        /*
         * simpleexp -> NUMBER | STRING | NIL | true | false | ... | constructor |
         * FUNCTION body | primaryexp
         */
        switch (this.baseLexer.t.token) {
            case BaseLexer.TK_NUMBER: {
                v.init(VKNUM, 0);
                v.setNval(this.baseLexer.t.r);
                break;
            }
            case BaseLexer.TK_STRING: {
                this.codestring(v, this.baseLexer.t.ts);
                break;
            }
            case BaseLexer.TK_NIL: {
                v.init(VNIL, 0);
                break;
            }
            case BaseLexer.TK_TRUE: {
                v.init(VTRUE, 0);
                break;
            }
            case BaseLexer.TK_FALSE: {
                v.init(VFALSE, 0);
                break;
            }
            case BaseLexer.TK_DOTS: { /* vararg */
                FuncState fs = this.fs;
                baseLexer.check_condition(fs.isVararg != 0, "cannot use '...' outside a vararg function");
                fs.isVararg &= ~FuncState.VARARG_NEEDSARG; /* don't need 'arg' */

                v.init(VVARARG, fs.codeABC(FuncState.OP_VARARG, 0, 1, 0));
                break;
            }
            case '{': { /* constructor */
                this.constructor(v);
                return;
            }
            case BaseLexer.TK_FUNCTION: {
                baseLexer.next();
                this.body(v, false, this.baseLexer.linenumber, null);
                return;
            }
            default: {
                this.primaryexp(v);
                return;
            }
        }
        baseLexer.next();
    }

    private int getunopr(int op) {
        switch (op) {
            case BaseLexer.TK_NOT:
                return OPR_NOT;
            case '-':
                return OPR_MINUS;
            case '#':
                return OPR_LEN;
            default:
                return OPR_NOUNOPR;
        }
    }

    private int getbinopr(int op) {
        switch (op) {
            case '+':
                return OPR_ADD;
            case '-':
                return OPR_SUB;
            case '*':
                return OPR_MUL;
            case '/':
                return OPR_DIV;
            case '%':
                return OPR_MOD;
            case '^':
                return OPR_POW;
            case BaseLexer.TK_CONCAT:
                return OPR_CONCAT;
            case BaseLexer.TK_NE:
                return OPR_NE;
            case BaseLexer.TK_EQ:
                return OPR_EQ;
            case '<':
                return OPR_LT;
            case BaseLexer.TK_LE:
                return OPR_LE;
            case '>':
                return OPR_GT;
            case BaseLexer.TK_GE:
                return OPR_GE;
            case BaseLexer.TK_AND:
                return OPR_AND;
            case BaseLexer.TK_OR:
                return OPR_OR;
            default:
                return OPR_NOBINOPR;
        }
    }

    /*
     ** subexpr -> (simpleexp | unop subexpr) { binop subexpr }
     ** where `binop' is any binary operator with a priority higher than `limit'
     */
    private int subexpr(expdesc v, int limit) {
        int op;
        int uop;
        this.enterlevel();
        uop = getunopr(this.baseLexer.t.token);
        if (uop != OPR_NOUNOPR) {
            baseLexer.next();
            this.subexpr(v, UNARY_PRIORITY);
            fs.prefix(uop, v);
        } else {
            this.simpleexp(v);
        }
        /* expand while operators have priorities higher than `limit' */
        op = getbinopr(this.baseLexer.t.token);
        while (op != OPR_NOBINOPR && priorityLeft[op] > limit) {
            expdesc v2 = new expdesc();
            int nextop;
            baseLexer.next();
            fs.infix(op, v);
            /* read sub-expression with higher priority */
            nextop = this.subexpr(v2, priorityRight[op]);
            fs.posfix(op, v, v2);
            op = nextop;
        }
        this.leavelevel();
        return op; /* return first untreated operator */
    }

    private void expr(expdesc v) {
        this.subexpr(v, 0);
    }

    /* }==================================================================== */
    /*
     ** {======================================================================
     ** Rules for Statements
     ** =======================================================================
     */
    private boolean block_follow(int token) {
        switch (token) {
            case BaseLexer.TK_ELSE:
            case BaseLexer.TK_ELSEIF:
            case BaseLexer.TK_END:
            case BaseLexer.TK_UNTIL:
            case BaseLexer.TK_EOS:
                return true;
            default:
                return false;
        }
    }

    private void block() {
        /* block -> chunk */
        FuncState fs = this.fs;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, false);
        this.chunk();
        FuncState._assert(bl.breaklist == NO_JUMP);
        fs.leaveblock();
    }

    /*
     ** check whether, in an assignment to a local variable, the local variable
     ** is needed in a previous assignment (to a table). If so, save original
     ** local value in a safe place and use this safe copy in the previous
     ** assignment.
     */
    private void check_conflict(LHS_assign lh, expdesc v) {
        FuncState fs = this.fs;
        int extra = fs.freereg;  /* eventual position to save local variable */

        boolean conflict = false;
        for (; lh != null; lh = lh.prev) {
            if (lh.v.k == VINDEXED) {
                if (lh.v.info == v.info) {  /* conflict? */
                    conflict = true;
                    lh.v.info = extra;  /* previous assignment will use safe copy */
                }
                if (lh.v.aux == v.info) {  /* conflict? */
                    conflict = true;
                    lh.v.aux = extra;  /* previous assignment will use safe copy */
                }
            }
        }
        if (conflict) {
            fs.codeABC(FuncState.OP_MOVE, fs.freereg, v.info, 0); /* make copy */
            fs.reserveregs(1);
        }
    }

    private void assignment(LHS_assign lh, int nvars) {
        expdesc e = new expdesc();
        baseLexer.check_condition(VLOCAL <= lh.v.k && lh.v.k <= VINDEXED,
                "syntax error");
        if (baseLexer.testnext(',')) {  /* assignment -> `,' primaryexp assignment */
            LHS_assign nv = new LHS_assign();
            nv.prev = lh;
            this.primaryexp(nv.v);
            if (nv.v.k == VLOCAL) {
                this.check_conflict(lh, nv.v);
            }
            this.assignment(nv, nvars + 1);
        } else {  /* assignment . `=' explist1 */
            int nexps;
            baseLexer.checknext('=');
            nexps = this.explist1(e);
            if (nexps != nvars) {
                this.adjust_assign(nvars, nexps, e);
                if (nexps > nvars) {
                    this.fs.freereg -= nexps - nvars;  /* remove extra values */
                }
            } else {
                fs.setoneret(e);  /* close last expression */

                fs.storevar(lh.v, e);
                return;  /* avoid default */
            }
        }
        e.init(VNONRELOC, this.fs.freereg - 1);  /* default assignment */

        fs.storevar(lh.v, e);
    }

    private int cond() {
        /* cond -> exp */
        expdesc v = new expdesc();
        /* read condition */
        this.expr(v);
        /* `falses' are all equal here */
        if (v.k == VNIL) {
            v.k = VFALSE;
        }
        fs.goiftrue(v);
        return v.f;
    }

    private void breakstat() {
        FuncState fs = this.fs;
        BlockCnt bl = fs.bl;
        boolean upval = false;
        while (bl != null && !bl.isbreakable) {
            upval |= bl.upval;
            bl = bl.previous;
        }
        if (bl == null) {
            baseLexer.syntaxerror("no loop to break");
        }
        if (upval) {
            fs.codeABC(FuncState.OP_CLOSE, bl.nactvar, 0, 0);
        }
        bl.breaklist = fs.concat(bl.breaklist, fs.jump());
    }

    private void whilestat(int line) {
        /* whilestat -> WHILE cond DO block END */
        FuncState fs = this.fs;
        int whileinit;
        int condexit;
        BlockCnt bl = new BlockCnt();
        baseLexer.next();  /* skip WHILE */

        whileinit = fs.getlabel();
        condexit = this.cond();
        fs.enterblock(bl, true);
        baseLexer.checknext(BaseLexer.TK_DO);
        this.block();
        fs.patchlist(fs.jump(), whileinit);
        baseLexer.check_match(BaseLexer.TK_END, BaseLexer.TK_WHILE, line);
        fs.leaveblock();
        fs.patchtohere(condexit);  /* false conditions finish the loop */
    }

    private void repeatstat(int line) {
        /* repeatstat -> REPEAT block UNTIL cond */
        int condexit;
        FuncState fs = this.fs;
        int repeat_init = fs.getlabel();
        BlockCnt bl1 = new BlockCnt();
        BlockCnt bl2 = new BlockCnt();
        fs.enterblock(bl1, true); /* loop block */

        fs.enterblock(bl2, false); /* scope block */

        baseLexer.next(); /* skip REPEAT */

        this.chunk();
        baseLexer.check_match(BaseLexer.TK_UNTIL, BaseLexer.TK_REPEAT, line);
        condexit = this.cond(); /* read condition (inside scope block) */

        if (!bl2.upval) { /* no upvalues? */
            fs.leaveblock(); /* finish scope */

            fs.patchlist(condexit, repeat_init); /* close the loop */
        } else { /* complete semantics when there are upvalues */
            this.breakstat(); /* if condition then break */

            fs.patchtohere(condexit); /* else... */

            fs.leaveblock(); /* finish scope... */

            fs.patchlist(fs.jump(), repeat_init); /* and repeat */
        }
        fs.leaveblock(); /* finish loop */
    }

    private void exp1() {
        expdesc e = new expdesc();
        this.expr(e);
        fs.exp2nextreg(e);
    }

    private void forbody(int base, int line, int nvars, boolean isnum) {
        /* forbody -> DO block */
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        int prep, endfor;
        this.adjustlocalvars(3); /* control variables */

        baseLexer.checknext(BaseLexer.TK_DO);
        prep = isnum ? fs.codeAsBx(FuncState.OP_FORPREP, base, NO_JUMP) : fs.jump();
        fs.enterblock(bl, false); /* scope for declared variables */

        this.adjustlocalvars(nvars);
        fs.reserveregs(nvars);
        this.block();
        fs.leaveblock(); /* end of scope for declared variables */

        fs.patchtohere(prep);
        endfor = (isnum) ? fs.codeAsBx(FuncState.OP_FORLOOP, base, NO_JUMP) :
                fs.codeABC(FuncState.OP_TFORLOOP, base, 0, nvars);
        fs.fixline(line); /* pretend that `Lua.OP_FOR' starts the loop */

        fs.patchlist((isnum ? endfor : fs.jump()), prep + 1);
    }

    private void fornum(String varname, int line) {
        /* fornum -> NAME = exp1,exp1[,exp1] forbody */
        FuncState fs = this.fs;
        int base = fs.freereg;
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_INDEX, 0);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_LIMIT, 1);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STEP, 2);
        this.new_localvar(varname, 3);
        baseLexer.checknext('=');
        this.exp1(); /* initial value */

        baseLexer.checknext(',');
        this.exp1(); /* limit */

        if (baseLexer.testnext(',')) {
            this.exp1(); /* optional step */
        } else { /* default step = 1 */
            fs.codeABx(FuncState.OP_LOADK, fs.freereg, fs.numberK(1));
            fs.reserveregs(1);
        }
        this.forbody(base, line, 1, true);
    }

    private void forlist(String indexname) {
        /* forlist -> NAME {,NAME} IN explist1 forbody */
        FuncState fs = this.fs;
        expdesc e = new expdesc();
        int nvars = 0;
        int line;
        int base = fs.freereg;
        /* create control variables */
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_GENERATOR, nvars++);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STATE, nvars++);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_CONTROL, nvars++);
        /* create declared variables */
        this.new_localvar(indexname, nvars++);
        while (baseLexer.testnext(',')) {
            this.new_localvar(baseLexer.str_checkname(), nvars++);
        }
        baseLexer.checknext(BaseLexer.TK_IN);
        line = this.baseLexer.linenumber;
        this.adjust_assign(3, this.explist1(e), e);
        fs.checkstack(3); /* extra space to call generator */

        this.forbody(base, line, nvars - 3, false);
    }

    private void forstat(int line) {
        /* forstat -> FOR (fornum | forlist) END */
        FuncState fs = this.fs;
        String varname;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, true); /* scope for loop and control variables */

        baseLexer.next(); /* skip `for' */

        varname = baseLexer.str_checkname(); /* first variable name */

        switch (this.baseLexer.t.token) {
            case '=':
                this.fornum(varname, line);
                break;
            case ',':
            case BaseLexer.TK_IN:
                this.forlist(varname);
                break;
            default:
                baseLexer.syntaxerror("'=' or 'in' expected");
        }
        baseLexer.check_match(BaseLexer.TK_END, BaseLexer.TK_FOR, line);
        fs.leaveblock(); /* loop scope (`break' jumps to this point) */
    }

    private int test_then_block() {
        /* test_then_block -> [IF | ELSEIF] cond THEN block */
        int condexit;
        baseLexer.next(); /* skip IF or ELSEIF */

        condexit = this.cond();
        baseLexer.checknext(BaseLexer.TK_THEN);
        this.block(); /* `then' part */

        return condexit;
    }

    private void ifstat(int line) {
        /* ifstat -> IF cond THEN block {ELSEIF cond THEN block} [ELSE block] END */
        FuncState fs = this.fs;
        int flist;
        int escapelist = NO_JUMP;
        flist = test_then_block(); /* IF cond THEN block */

        while (this.baseLexer.t.token == BaseLexer.TK_ELSEIF) {
            escapelist = fs.concat(escapelist, fs.jump());
            fs.patchtohere(flist);
            flist = test_then_block(); /* ELSEIF cond THEN block */
        }
        if (this.baseLexer.t.token == BaseLexer.TK_ELSE) {
            escapelist = fs.concat(escapelist, fs.jump());
            fs.patchtohere(flist);
            baseLexer.next(); /* skip ELSE (after patch, for correct line info) */

            this.block(); /* `else' part */
        } else {
            escapelist = fs.concat(escapelist, flist);
        }
        fs.patchtohere(escapelist);
        baseLexer.check_match(BaseLexer.TK_END, BaseLexer.TK_IF, line);
    }

    private void localfunc() {
        expdesc v = new expdesc();
        expdesc b = new expdesc();
        FuncState fs = this.fs;
        String name = baseLexer.str_checkname();
        this.new_localvar(name, 0);
        v.init(VLOCAL, fs.freereg);
        fs.reserveregs(1);
        this.adjustlocalvars(1);
        this.body(b, false, this.baseLexer.linenumber, name);
        fs.storevar(v, b);
        /* debug information will only see the variable after this point! */
    }

    private void localstat() {
        /* stat -> LOCAL NAME {`,' NAME} [`=' explist1] */
        int nvars = 0;
        int nexps;
        expdesc e = new expdesc();
        do {
            this.new_localvar(baseLexer.str_checkname(), nvars++);
        } while (baseLexer.testnext(','));
        if (baseLexer.testnext('=')) {
            nexps = this.explist1(e);
        } else {
            e.k = VVOID;
            nexps = 0;
        }
        this.adjust_assign(nvars, nexps, e);
        this.adjustlocalvars(nvars);
    }

    private boolean funcname(expdesc v) {
        /* funcname -> NAME {field} [`:' NAME] */
        boolean needself = false;
        this.singlevar(v);
        String refname = v.lastname;
        while (this.baseLexer.t.token == '.') {
            this.field(v);
            refname += "." + v.lastname;
        }
        if (this.baseLexer.t.token == ':') {
            needself = true;
            this.field(v);
            refname += ":" + v.lastname;
        }
        v.lastname = refname;
        return needself;
    }

    private void funcstat(int line) {
        /* funcstat -> FUNCTION funcname body */
        boolean needself;
        expdesc v = new expdesc();
        expdesc b = new expdesc();
        baseLexer.next(); /* skip FUNCTION */

        needself = this.funcname(v);
        this.body(b, needself, line, v.lastname);
        fs.storevar(v, b);
        fs.fixline(line); /* definition `happens' in the first line */
    }

    private void exprstat() {
        /* stat -> func | assignment */
        FuncState fs = this.fs;
        LHS_assign v = new LHS_assign();
        this.primaryexp(v.v);
        if (v.v.k == VCALL) /* stat -> func */ {
            FuncState.SETARG_C(fs.getcodePtr(v.v), 1); /* call statement uses no results */
        } else { /* stat -> assignment */
            v.prev = null;
            this.assignment(v, 1);
        }
    }

    private void retstat() {
        /* stat -> RETURN explist */
        FuncState fs = this.fs;
        expdesc e = new expdesc();
        int first, nret; /* registers with returned values */

        baseLexer.next(); /* skip RETURN */

        if (block_follow(this.baseLexer.t.token) || this.baseLexer.t.token == ';') {
            first = nret = 0; /* return no values */
        } else {
            nret = this.explist1(e); /* optional return values */

            if (hasmultret(e.k)) {
                fs.setmultret(e);
                if (e.k == VCALL && nret == 1) { /* tail call? */
                    FuncState.SET_OPCODE(fs.getcodePtr(e), FuncState.OP_TAILCALL);
                    FuncState._assert(FuncState.GETARG_A(fs.getcode(e)) == fs.nactvar);
                }
                first = fs.nactvar;
                nret = FuncState.LUA_MULTRET; /* return all values */
            } else {
                if (nret == 1) /* only one single value? */ {
                    first = fs.exp2anyreg(e);
                } else {
                    fs.exp2nextreg(e); /* values must go to the `stack' */

                    first = fs.nactvar; /* return all `active' values */

                    FuncState._assert(nret == fs.freereg - first);
                }
            }
        }
        fs.ret(first, nret);
    }

    private boolean statement() {
        int line = this.baseLexer.linenumber; /* may be needed for error messages */
        switch (this.baseLexer.t.token) {
            case BaseLexer.TK_IF: { /* stat -> ifstat */
                this.ifstat(line);
                return false;
            }
            case BaseLexer.TK_WHILE: { /* stat -> whilestat */
                this.whilestat(line);
                return false;
            }
            case BaseLexer.TK_DO: { /* stat -> DO block END */
                baseLexer.next(); /* skip DO */

                this.block();
                baseLexer.check_match(BaseLexer.TK_END, BaseLexer.TK_DO, line);
                return false;
            }
            case BaseLexer.TK_FOR: { /* stat -> forstat */
                this.forstat(line);
                return false;
            }
            case BaseLexer.TK_REPEAT: { /* stat -> repeatstat */
                this.repeatstat(line);
                return false;
            }
            case BaseLexer.TK_FUNCTION: {
                this.funcstat(line); /* stat -> funcstat */

                return false;
            }
            case BaseLexer.TK_LOCAL: { /* stat -> localstat */
                baseLexer.next(); /* skip LOCAL */

                if (baseLexer.testnext(BaseLexer.TK_FUNCTION)) /* local function? */ {
                    this.localfunc();
                } else {
                    this.localstat();
                }
                return false;
            }
            case BaseLexer.TK_RETURN: { /* stat -> retstat */
                this.retstat();
                return true; /* must be last statement */
            }
            case BaseLexer.TK_BREAK: { /* stat -> breakstat */
                baseLexer.next(); /* skip BREAK */

                this.breakstat();
                return true; /* must be last statement */
            }
            default: {
                this.exprstat();
                return false; /* to avoid warnings */

            }
        }
    }

    private void chunk() {
        /* chunk -> { stat [`;'] } */
        boolean islast = false;
        this.enterlevel();
        while (!islast && !block_follow(this.baseLexer.t.token)) {
            islast = this.statement();
            baseLexer.testnext(';');
            FuncState._assert(this.fs.f.maxStacksize >= this.fs.freereg
                    && this.fs.freereg >= this.fs.nactvar);
            this.fs.freereg = this.fs.nactvar; /* free registers */
        }
        this.leavelevel();
    }

    static class expdesc {
        int k; // expkind, from enumerated list, above

        int info, aux;
        String lastname = null;
        int t; /* patch list of `exit when true' */
        int f; /* patch list of `exit when false' */
        private double _nval;
        private boolean has_nval;

        public void setNval(double r) {
            _nval = r;
            has_nval = true;
        }

        public double nval() {
            return has_nval ? _nval : info;
        }

        void init(int k, int i) {
            this.f = NO_JUMP;
            this.t = NO_JUMP;
            this.k = k;
            this.info = i;
        }

        boolean hasjumps() {
            return (t != f);
        }

        boolean isnumeral() {
            return (k == VKNUM && t == NO_JUMP && f == NO_JUMP);
        }

        public void setvalue(expdesc other) {
            this.k = other.k;
            this._nval = other._nval;
            this.has_nval = other.has_nval;
            this.info = other.info;
            this.aux = other.aux;
            this.t = other.t;
            this.f = other.f;
        }
    }

    /*
     ** {======================================================================
     ** Rules for Constructors
     ** =======================================================================
     */
    static class ConsControl {
        final expdesc v = new expdesc(); /* last list item read */
        expdesc t; /* table descriptor */
        int nh; /* total number of `record' elements */
        int na; /* total number of array elements */
        int tostore; /* number of array elements pending to be stored */
    }

    /*
     ** structure to chain all variables in the left-hand side of an
     ** assignment
     */
    static class LHS_assign {
        /* variable (global, local, upvalue, or indexed) */
        final expdesc v = new expdesc();
        LHS_assign prev;
    }
}
