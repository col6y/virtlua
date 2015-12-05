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
    static final int OPR_NOBINOPR = 15;
    static final int OPR_NOUNOPR = 3;
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
    private final BaseLexer lexer;
    private FuncState fs;  /* `FuncState' is private to the parser */

    private LexState(int firstByte, Reader stream, String name) {
        lexer = new BaseLexer(firstByte, stream, name);
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

    private void compile() {
        chunk();
        lexer.check(Token.TK_EOS);
        close_func();
        FuncState._assert(fs == null);
    }

    private void chunk() {
        /* chunk -> { stat [`;'] } */
        while (!lexer.getToken().isBlockTerminator()) {
            boolean islast = statement();
            lexer.testnext(';');
            FuncState._assert(fs.f.maxStacksize >= fs.freereg && fs.freereg >= fs.nactvar);
            fs.freereg = fs.nactvar; /* free registers */
            if (islast) {
                break;
            }
        }
    }

    // *** STATEMENTS ***

    private boolean statement() {
        int line = lexer.getLine(); /* may be needed for error messages */
        switch (lexer.switchToken()) {
            case Token.TK_IF: /* stat -> ifstat */
                ifstat(line);
                return false;
            case Token.TK_WHILE: /* stat -> whilestat */
                whilestat(line);
                return false;
            case Token.TK_DO: /* stat -> DO block END */
                lexer.next(); /* skip DO */
                block();
                lexer.check_match(Token.TK_END, Token.TK_DO, line);
                return false;
            case Token.TK_FOR: /* stat -> forstat */
                forstat(line);
                return false;
            case Token.TK_REPEAT: /* stat -> repeatstat */
                repeatstat(line);
                return false;
            case Token.TK_FUNCTION:
                funcstat(line); /* stat -> funcstat */
                return false;
            case Token.TK_LOCAL: /* stat -> localstat */
                lexer.next(); /* skip LOCAL */
                if (lexer.testnext(Token.TK_FUNCTION)) /* local function? */ {
                    localfunc();
                } else {
                    localstat();
                }
                return false;
            case Token.TK_RETURN: /* stat -> retstat */
                retstat();
                return true; /* must be last statement */
            case Token.TK_BREAK: /* stat -> breakstat */
                lexer.next(); /* skip BREAK */
                breakstat();
                return true; /* must be last statement */
            default:
                exprstat();
                return false; /* to avoid warnings */
        }
    }

    private void ifstat(int line) {
        /* ifstat -> IF cond THEN block {ELSEIF cond THEN block} [ELSE block] END */
        int escapelist = NO_JUMP;
        int flist = test_then_block(); /* IF cond THEN block */
        while (lexer.test(Token.TK_ELSEIF)) {
            escapelist = fs.concat(escapelist, fs.jump(lexer.getLastLine()));
            fs.patchtohere(flist);
            flist = test_then_block(); /* ELSEIF cond THEN block */
        }
        if (lexer.test(Token.TK_ELSE)) {
            escapelist = fs.concat(escapelist, fs.jump(lexer.getLastLine()));
            fs.patchtohere(flist);
            lexer.next(); /* skip ELSE (after patch, for correct line info) */
            block(); /* `else' part */
        } else {
            escapelist = fs.concat(escapelist, flist);
        }
        fs.patchtohere(escapelist);
        lexer.check_match(Token.TK_END, Token.TK_IF, line);
    }

    private int test_then_block() {
        /* test_then_block -> [IF | ELSEIF] cond THEN block */
        lexer.next(); /* skip IF or ELSEIF */
        int condexit = cond();
        lexer.checknext(Token.TK_THEN);
        block(); /* `then' part */
        return condexit;
    }

    // *** EXPRESSIONS ***

    private int cond() {
        /* cond -> exp */
        expdesc v = new expdesc();
        /* read condition */
        expr(v);
        /* `falses' are all equal here */
        if (v.k == VNIL) {
            v.k = VFALSE;
        }
        fs.goiftrue(v, lexer.getLastLine());
        return v.f;
    }

    // *** UNPORTED ***

    void syntaxerror(String msg) {
        lexer.syntaxerror(msg);
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

    private void checkname(expdesc e) {
        codestring(e, lexer.checkname_next());
    }

    private void codestring(expdesc e, String s) {
        e.init(VK, fs.stringK(s));
    }

    private int registerlocalvar(String varname) {
        if (fs.locvars == null || fs.nlocvars + 1 > fs.locvars.length) {
            fs.locvars = FuncState.realloc(fs.locvars, fs.nlocvars * 2 + 1);
        }
        fs.locvars[fs.nlocvars] = varname;
        return fs.nlocvars++;
    }

    private void new_localvar(String name, int n) {
        fs.checklimit(fs.nactvar + n + 1, FuncState.LUAI_MAXVARS, "local variables");
        fs.actvar[fs.nactvar + n] = (short) registerlocalvar(name);
    }

    private void adjustlocalvars(int nvars) {
        fs.nactvar = (fs.nactvar + nvars);
    }

    private void singlevar(expdesc var) {
        String varname = lexer.checkname_next();
        var.lastname = varname;
        if (fs.singlevaraux(varname, var, 1) == VGLOBAL) {
            var.info = fs.stringK(varname); /* info points to global name */
        }
    }

    private void adjust_assign(int nvars, int nexps, expdesc e, int lastline) {
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
                fs.exp2nextreg(e, lastline);
            }
            if (extra > 0) {
                int reg = fs.freereg;
                fs.reserveregs(extra);
                fs.nil(reg, extra, lexer.getLastLine());
            }
        }
    }

    private void pushclosure(FuncState func, expdesc v) {
        LuaPrototype f = fs.f;
        if (f.prototypes == null || fs.np + 1 > f.prototypes.length) {
            f.prototypes = FuncState.realloc(f.prototypes, fs.np * 2 + 1);
        }
        f.prototypes[fs.np++] = func.f;
        v.init(VRELOCABLE, fs.codeABx(FuncState.OP_CLOSURE, 0, fs.np - 1, lexer.getLastLine()));
        for (int i = 0; i < func.f.numUpvalues; i++) {
            int o = (func.upvalues_k[i] == VLOCAL) ? FuncState.OP_MOVE : FuncState.OP_GETUPVAL;
            fs.codeABC(o, 0, func.upvalues_info[i], 0, lexer.getLastLine());
        }
    }

    private void close_func() {
        LuaPrototype f = fs.f;
        f.isVararg = fs.isVararg != 0;

        fs.nactvar = 0;
        fs.ret(0, 0, lexer.getLastLine()); /* final return */

        f.code = FuncState.realloc(f.code, fs.pc);
        f.lines = FuncState.realloc(f.lines, fs.pc);
        f.constants = FuncState.realloc(f.constants, fs.nk);
        f.prototypes = FuncState.realloc(f.prototypes, fs.np);
        fs.locvars = FuncState.realloc(fs.locvars, fs.nlocvars);
        fs.upvalues = FuncState.realloc(fs.upvalues, f.numUpvalues);
        FuncState._assert(fs.bl == null);
        fs = fs.prev;
    }

    private void field(expdesc v) {
        /* field -> ['.' | ':'] NAME */
        expdesc key = new expdesc();
        fs.exp2anyreg(v, lexer.getLastLine());
        lexer.next(); /* skip the dot or colon */

        v.lastname = lexer.checkname();
        checkname(key);
        fs.indexed(v, key, lexer.getLastLine());
    }

    private void yindex(expdesc v) {
        /* index -> '[' expr ']' */
        lexer.next(); /* skip the '[' */

        expr(v);
        fs.exp2val(v, lexer.getLastLine());
        lexer.checknext(']');
    }

    private void recfield(ConsControl cc) {
        /* recfield -> (NAME | `['exp1`]') = exp1 */
        int reg = fs.freereg;
        expdesc key = new expdesc();
        expdesc val = new expdesc();
        int rkkey;
        if (lexer.test(Token.TK_NAME)) {
            fs.checklimit(cc.nh, BaseLexer.MAX_INT, "items in a constructor");
            checkname(key);
        } else /* lexer.test('[') */ {
            yindex(key);
        }
        cc.nh++;
        lexer.checknext('=');
        rkkey = fs.exp2RK(key, lexer.getLastLine());
        expr(val);
        fs.codeABC(FuncState.OP_SETTABLE, cc.t.info, rkkey, fs.exp2RK(val, lexer.getLastLine()), lexer.getLastLine());
        fs.freereg = reg; /* free registers */
    }

    private void listfield(ConsControl cc) {
        expr(cc.v);
        fs.checklimit(cc.na, BaseLexer.MAX_INT, "items in a constructor");
        cc.na++;
        cc.tostore++;
    }

    private void constructor(expdesc t) {
        /* constructor -> ?? */
        int line = lexer.getLine();
        int pc = fs.codeABC(FuncState.OP_NEWTABLE, 0, 0, 0, lexer.getLastLine());
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        t.init(VRELOCABLE, pc);
        cc.v.init(VVOID, 0); /* no value (yet) */

        fs.exp2nextreg(t, lexer.getLastLine()); /* fix it at stack top (for gc) */

        lexer.checknext('{');
        do {
            FuncState._assert(cc.v.k == VVOID || cc.tostore > 0);
            if (lexer.test('}')) {
                break;
            }
            fs.closelistfield(cc, lexer.getLastLine());
            switch (lexer.switchToken()) {
                case Token.TK_NAME: { /* may be listfields or recfields */
                    if (lexer.isLookahead('=')) {
                        recfield(cc);
                    } else {
                        listfield(cc);
                    }
                    break;
                }
                case '[': { /* constructor_item -> recfield */
                    recfield(cc);
                    break;
                }
                default: { /* constructor_part -> listfield */
                    listfield(cc);
                    break;
                }
            }
        } while (lexer.testnext(',') || lexer.testnext(';'));
        lexer.check_match('}', '{', line);
        fs.lastlistfield(cc, lexer.getLastLine());
        InstructionPtr i = new InstructionPtr(fs.f.code, pc);
        FuncState.SETARG_B(i, luaO_int2fb(cc.na)); /* set initial array size */
        FuncState.SETARG_C(i, luaO_int2fb(cc.nh));  /* set initial table size */
    }

    private void parlist() {
        /* parlist -> [ param { `,' param } ] */
        LuaPrototype f = fs.f;
        int nparams = 0;
        fs.isVararg = 0;
        if (!lexer.test(')')) {  /* is `parlist' not empty? */
            do {
                switch (lexer.switchToken()) {
                    case Token.TK_NAME: {  /* param . NAME */
                        new_localvar(lexer.checkname_next(), nparams++);
                        break;
                    }
                    case Token.TK_DOTS: {  /* param . `...' */
                        lexer.next();
                        fs.isVararg |= FuncState.VARARG_ISVARARG;
                        break;
                    }
                    default:
                        lexer.syntaxerror("<name> or '...' expected");
                }
            } while ((fs.isVararg == 0) && lexer.testnext(','));
        }
        adjustlocalvars(nparams);
        f.numParams = (fs.nactvar - (fs.isVararg & FuncState.VARARG_HASARG));
        fs.reserveregs(fs.nactvar);  /* reserve register for parameters */
    }

    private void body(expdesc e, boolean needself, int line, String fname) {
        /* body -> `(' parlist `)' chunk END */
        String func_name = fname == null ? fs.f.name : fs.f.name + "/" + fname;
        fs = new FuncState(func_name, fs, this, line);
        FuncState new_fs = fs;
        lexer.checknext('(');
        if (needself) {
            new_localvar("self", 0);
            adjustlocalvars(1);
        }
        parlist();
        lexer.checknext(')');
        chunk();
        lexer.check_match(Token.TK_END, Token.TK_FUNCTION, line);
        close_func();
        pushclosure(new_fs, e);
    }

    private int explist1(expdesc v) {
        /* explist1 -> expr { `,' expr } */
        int n = 1; /* at least one expression */
        expr(v);
        while (lexer.testnext(',')) {
            fs.exp2nextreg(v, lexer.getLastLine());
            expr(v);
            n++;
        }
        return n;
    }

    private void funcargs(expdesc f) {
        expdesc args = new expdesc();
        int base, nparams;
        int line = lexer.getLine();
        switch (lexer.switchToken()) {
            case '(': { /* funcargs -> `(' [ explist1 ] `)' */
                if (line != lexer.getLastLine()) {
                    lexer.syntaxerror("ambiguous syntax (function call x new statement)");
                }
                lexer.next();
                if (lexer.test(')')) /* arg list is empty? */ {
                    args.k = VVOID;
                } else {
                    explist1(args);
                    fs.setmultret(args);
                }
                lexer.check_match(')', '(', line);
                break;
            }
            case '{': { /* funcargs -> constructor */
                constructor(args);
                break;
            }
            case Token.TK_STRING: { /* funcargs -> STRING */
                codestring(args, lexer.checkstring());
                lexer.next(); /* must use `seminfo' before `next' */
                break;
            }
            default: {
                lexer.syntaxerror("function arguments expected");
                return;
            }
        }
        FuncState._assert(f.k == VNONRELOC);
        base = f.info; /* base register for call */

        if (hasmultret(args.k)) {
            nparams = FuncState.LUA_MULTRET; /* open call */
        } else {
            if (args.k != VVOID) {
                fs.exp2nextreg(args, lexer.getLastLine()); /* close last argument */
            }
            nparams = fs.freereg - (base + 1);
        }
        f.init(VCALL, fs.codeABC(FuncState.OP_CALL, base, nparams + 1, 2, lexer.getLastLine()));
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
        switch (lexer.switchToken()) {
            case '(': {
                int line = lexer.getLine();
                lexer.next();
                expr(v);
                lexer.check_match(')', '(', line);
                fs.dischargevars(v, lexer.getLastLine());
                break;
            }
            case Token.TK_NAME: {
                singlevar(v);
                break;
            }
            default: {
                lexer.syntaxerror("unexpected symbol");
                break;
            }
        }
    }

    private void primaryexp(expdesc v) {
        /*
         * primaryexp -> prefixexp { `.' NAME | `[' exp `]' | `:' NAME funcargs |
         * funcargs }
         */
        prefixexp(v);
        for (; ; ) {
            switch (lexer.switchToken()) {
                case '.': { /* field */
                    field(v);
                    break;
                }
                case '[': { /* `[' exp1 `]' */
                    expdesc key = new expdesc();
                    fs.exp2anyreg(v, lexer.getLastLine());
                    yindex(key);
                    fs.indexed(v, key, lexer.getLastLine());
                    break;
                }
                case ':': { /* `:' NAME funcargs */
                    expdesc key = new expdesc();
                    lexer.next();
                    checkname(key);
                    fs.self(v, key, lexer.getLastLine());
                    funcargs(v);
                    break;
                }
                case '(':
                case Token.TK_STRING:
                case '{': { /* funcargs */
                    fs.exp2nextreg(v, lexer.getLastLine());
                    funcargs(v);
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
        switch (lexer.switchToken()) {
            case Token.TK_NUMBER: {
                v.init(VKNUM, 0);
                v.setNval(lexer.checknumber());
                break;
            }
            case Token.TK_STRING: {
                codestring(v, lexer.checkstring());
                break;
            }
            case Token.TK_NIL: {
                v.init(VNIL, 0);
                break;
            }
            case Token.TK_TRUE: {
                v.init(VTRUE, 0);
                break;
            }
            case Token.TK_FALSE: {
                v.init(VFALSE, 0);
                break;
            }
            case Token.TK_DOTS: { /* vararg */
                if (fs.isVararg == 0) {
                    lexer.syntaxerror("cannot use '...' outside a vararg function");
                }
                fs.isVararg &= ~FuncState.VARARG_NEEDSARG; /* don't need 'arg' */

                v.init(VVARARG, fs.codeABC(FuncState.OP_VARARG, 0, 1, 0, lexer.getLastLine()));
                break;
            }
            case '{': { /* constructor */
                constructor(v);
                return;
            }
            case Token.TK_FUNCTION: {
                lexer.next();
                body(v, false, lexer.getLine(), null);
                return;
            }
            default: {
                primaryexp(v);
                return;
            }
        }
        lexer.next();
    }

    /*
     ** subexpr -> (simpleexp | unop subexpr) { binop subexpr }
     ** where `binop' is any binary operator with a priority higher than `limit'
     */
    private int subexpr(expdesc v, int limit) {
        int uop = lexer.getToken().toUnary();
        if (uop != OPR_NOUNOPR) {
            lexer.next();
            subexpr(v, UNARY_PRIORITY);
            fs.prefix(uop, v, lexer.getLastLine());
        } else {
            simpleexp(v);
        }
        /* expand while operators have priorities higher than `limit' */
        int op = lexer.getToken().toBinary();
        while (op != OPR_NOBINOPR && priorityLeft[op] > limit) {
            expdesc v2 = new expdesc();
            int nextop;
            lexer.next();
            fs.infix(op, v, lexer.getLastLine());
            /* read sub-expression with higher priority */
            nextop = subexpr(v2, priorityRight[op]);
            fs.posfix(op, v, v2, lexer.getLastLine());
            op = nextop;
        }
        return op; /* return first untreated operator */
    }

    private void expr(expdesc v) {
        subexpr(v, 0);
    }

    private void block() {
        /* block -> chunk */
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, false);
        chunk();
        FuncState._assert(bl.breaklist == NO_JUMP);
        fs.leaveblock(fs, lexer.getLastLine());
    }

    /*
     ** check whether, in an assignment to a local variable, the local variable
     ** is needed in a previous assignment (to a table). If so, save original
     ** local value in a safe place and use this safe copy in the previous
     ** assignment.
     */
    private void check_conflict(LHS_assign lh, expdesc v) {
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
            fs.codeABC(FuncState.OP_MOVE, fs.freereg, v.info, 0, lexer.getLastLine()); /* make copy */
            fs.reserveregs(1);
        }
    }

    private void assignment(LHS_assign lh, int nvars) {
        expdesc e = new expdesc();
        if (VLOCAL > lh.v.k || lh.v.k > VINDEXED) {
            lexer.syntaxerror("syntax error");
        }
        if (lexer.testnext(',')) {  /* assignment -> `,' primaryexp assignment */
            LHS_assign nv = new LHS_assign();
            nv.prev = lh;
            primaryexp(nv.v);
            if (nv.v.k == VLOCAL) {
                check_conflict(lh, nv.v);
            }
            assignment(nv, nvars + 1);
        } else {  /* assignment . `=' explist1 */
            int nexps;
            lexer.checknext('=');
            nexps = explist1(e);
            if (nexps != nvars) {
                adjust_assign(nvars, nexps, e, lexer.getLastLine());
                if (nexps > nvars) {
                    fs.freereg -= nexps - nvars;  /* remove extra values */
                }
            } else {
                fs.setoneret(e);  /* close last expression */

                fs.storevar(lh.v, e, lexer.getLastLine());
                return;  /* avoid default */
            }
        }
        e.init(VNONRELOC, fs.freereg - 1);  /* default assignment */

        fs.storevar(lh.v, e, lexer.getLastLine());
    }

    private void breakstat() {
        BlockCnt bl = fs.bl;
        boolean upval = false;
        while (bl != null && !bl.isbreakable) {
            upval |= bl.upval;
            bl = bl.previous;
        }
        if (bl == null) {
            lexer.syntaxerror("no loop to break");
        }
        if (upval) {
            fs.codeABC(FuncState.OP_CLOSE, bl.nactvar, 0, 0, lexer.getLastLine());
        }
        bl.breaklist = fs.concat(bl.breaklist, fs.jump(lexer.getLastLine()));
    }

    private void whilestat(int line) {
        /* whilestat -> WHILE cond DO block END */
        int whileinit;
        int condexit;
        BlockCnt bl = new BlockCnt();
        lexer.next();  /* skip WHILE */

        whileinit = fs.getlabel();
        condexit = cond();
        fs.enterblock(bl, true);
        lexer.checknext(Token.TK_DO);
        block();
        fs.patchlist(fs.jump(lexer.getLastLine()), whileinit);
        lexer.check_match(Token.TK_END, Token.TK_WHILE, line);
        fs.leaveblock(fs, lexer.getLastLine());
        fs.patchtohere(condexit);  /* false conditions finish the loop */
    }

    private void repeatstat(int line) {
        /* repeatstat -> REPEAT block UNTIL cond */
        int condexit;
        int repeat_init = fs.getlabel();
        BlockCnt bl1 = new BlockCnt();
        BlockCnt bl2 = new BlockCnt();
        fs.enterblock(bl1, true); /* loop block */

        fs.enterblock(bl2, false); /* scope block */

        lexer.next(); /* skip REPEAT */

        chunk();
        lexer.check_match(Token.TK_UNTIL, Token.TK_REPEAT, line);
        condexit = cond(); /* read condition (inside scope block) */

        if (!bl2.upval) { /* no upvalues? */
            fs.leaveblock(fs, lexer.getLastLine()); /* finish scope */

            fs.patchlist(condexit, repeat_init); /* close the loop */
        } else { /* complete semantics when there are upvalues */
            breakstat(); /* if condition then break */

            fs.patchtohere(condexit); /* else... */

            fs.leaveblock(fs, lexer.getLastLine()); /* finish scope... */

            fs.patchlist(fs.jump(lexer.getLastLine()), repeat_init); /* and repeat */
        }
        fs.leaveblock(fs, lexer.getLastLine()); /* finish loop */
    }

    private void exp1() {
        expdesc e = new expdesc();
        expr(e);
        fs.exp2nextreg(e, lexer.getLastLine());
    }

    private void forbody(int base, int line, int nvars, boolean isnum) {
        /* forbody -> DO block */
        BlockCnt bl = new BlockCnt();
        int prep, endfor;
        adjustlocalvars(3); /* control variables */

        lexer.checknext(Token.TK_DO);
        prep = isnum ? fs.codeAsBx(FuncState.OP_FORPREP, base, NO_JUMP, lexer.getLastLine()) : fs.jump(lexer.getLastLine());
        fs.enterblock(bl, false); /* scope for declared variables */

        adjustlocalvars(nvars);
        fs.reserveregs(nvars);
        block();
        fs.leaveblock(fs, lexer.getLastLine()); /* end of scope for declared variables */

        fs.patchtohere(prep);
        endfor = (isnum) ? fs.codeAsBx(FuncState.OP_FORLOOP, base, NO_JUMP, lexer.getLastLine()) :
                fs.codeABC(FuncState.OP_TFORLOOP, base, 0, nvars, lexer.getLastLine());
        fs.fixline(line); /* pretend that `Lua.OP_FOR' starts the loop */

        fs.patchlist((isnum ? endfor : fs.jump(lexer.getLastLine())), prep + 1);
    }

    private void fornum(String varname, int line) {
        /* fornum -> NAME = exp1,exp1[,exp1] forbody */
        int base = fs.freereg;
        new_localvar(RESERVED_LOCAL_VAR_FOR_INDEX, 0);
        new_localvar(RESERVED_LOCAL_VAR_FOR_LIMIT, 1);
        new_localvar(RESERVED_LOCAL_VAR_FOR_STEP, 2);
        new_localvar(varname, 3);
        lexer.checknext('=');
        exp1(); /* initial value */

        lexer.checknext(',');
        exp1(); /* limit */

        if (lexer.testnext(',')) {
            exp1(); /* optional step */
        } else { /* default step = 1 */
            fs.codeABx(FuncState.OP_LOADK, fs.freereg, fs.numberK(1), lexer.getLastLine());
            fs.reserveregs(1);
        }
        forbody(base, line, 1, true);
    }

    private void forlist(String indexname) {
        /* forlist -> NAME {,NAME} IN explist1 forbody */
        expdesc e = new expdesc();
        int nvars = 0;
        int line;
        int base = fs.freereg;
        /* create control variables */
        new_localvar(RESERVED_LOCAL_VAR_FOR_GENERATOR, nvars++);
        new_localvar(RESERVED_LOCAL_VAR_FOR_STATE, nvars++);
        new_localvar(RESERVED_LOCAL_VAR_FOR_CONTROL, nvars++);
        /* create declared variables */
        new_localvar(indexname, nvars++);
        while (lexer.testnext(',')) {
            new_localvar(lexer.checkname_next(), nvars++);
        }
        lexer.checknext(Token.TK_IN);
        line = lexer.getLine();
        adjust_assign(3, explist1(e), e, lexer.getLastLine());
        fs.checkstack(3); /* extra space to call generator */

        forbody(base, line, nvars - 3, false);
    }

    private void forstat(int line) {
        /* forstat -> FOR (fornum | forlist) END */
        String varname;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, true); /* scope for loop and control variables */

        lexer.next(); /* skip `for' */

        varname = lexer.checkname_next(); /* first variable name */

        switch (lexer.switchToken()) {
            case '=':
                fornum(varname, line);
                break;
            case ',':
            case Token.TK_IN:
                forlist(varname);
                break;
            default:
                lexer.syntaxerror("'=' or 'in' expected");
        }
        lexer.check_match(Token.TK_END, Token.TK_FOR, line);
        fs.leaveblock(fs, lexer.getLastLine()); /* loop scope (`break' jumps to this point) */
    }

    private void localfunc() {
        expdesc v = new expdesc();
        expdesc b = new expdesc();
        String name = lexer.checkname_next();
        new_localvar(name, 0);
        v.init(VLOCAL, fs.freereg);
        fs.reserveregs(1);
        adjustlocalvars(1);
        body(b, false, lexer.getLine(), name);
        fs.storevar(v, b, lexer.getLastLine());
        /* debug information will only see the variable after this point! */
    }

    private void localstat() {
        /* stat -> LOCAL NAME {`,' NAME} [`=' explist1] */
        int nvars = 0;
        int nexps;
        expdesc e = new expdesc();
        do {
            new_localvar(lexer.checkname_next(), nvars++);
        } while (lexer.testnext(','));
        if (lexer.testnext('=')) {
            nexps = explist1(e);
        } else {
            e.k = VVOID;
            nexps = 0;
        }
        adjust_assign(nvars, nexps, e, lexer.getLastLine());
        adjustlocalvars(nvars);
    }

    private boolean funcname(expdesc v) {
        /* funcname -> NAME {field} [`:' NAME] */
        boolean needself = false;
        singlevar(v);
        String refname = v.lastname;
        while (lexer.test('.')) {
            field(v);
            refname += "." + v.lastname;
        }
        if (lexer.test(':')) {
            needself = true;
            field(v);
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
        lexer.next(); /* skip FUNCTION */

        needself = funcname(v);
        body(b, needself, line, v.lastname);
        fs.storevar(v, b, lexer.getLastLine());
        fs.fixline(line); /* definition `happens' in the first line */
    }

    private void exprstat() {
        /* stat -> func | assignment */
        LHS_assign v = new LHS_assign();
        primaryexp(v.v);
        if (v.v.k == VCALL) /* stat -> func */ {
            FuncState.SETARG_C(fs.getcodePtr(v.v), 1); /* call statement uses no results */
        } else { /* stat -> assignment */
            v.prev = null;
            assignment(v, 1);
        }
    }

    private void retstat() {
        /* stat -> RETURN explist */
        expdesc e = new expdesc();
        int first, nret; /* registers with returned values */

        lexer.next(); /* skip RETURN */

        if (lexer.getToken().isBlockTerminator() || lexer.test(';')) {
            first = nret = 0; /* return no values */
        } else {
            nret = explist1(e); /* optional return values */

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
                    first = fs.exp2anyreg(e, lexer.getLastLine());
                } else {
                    fs.exp2nextreg(e, lexer.getLastLine()); /* values must go to the `stack' */

                    first = fs.nactvar; /* return all `active' values */

                    FuncState._assert(nret == fs.freereg - first);
                }
            }
        }
        fs.ret(first, nret, lexer.getLastLine());
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
            f = NO_JUMP;
            t = NO_JUMP;
            this.k = k;
            info = i;
        }

        boolean hasjumps() {
            return (t != f);
        }

        boolean isnumeral() {
            return (k == VKNUM && t == NO_JUMP && f == NO_JUMP);
        }

        public void setvalue(expdesc other) {
            k = other.k;
            _nval = other._nval;
            has_nval = other.has_nval;
            info = other.info;
            aux = other.aux;
            t = other.t;
            f = other.f;
        }
    }

    static class ConsControl {
        final expdesc v = new expdesc(); /* last list item read */
        expdesc t; /* table descriptor */
        int nh; /* total number of `record' elements */
        int na; /* total number of array elements */
        int tostore; /* number of array elements pending to be stored */
    }

    static class LHS_assign {
        /* variable (global, local, upvalue, or indexed) */
        final expdesc v = new expdesc();
        LHS_assign prev;
    }
}
