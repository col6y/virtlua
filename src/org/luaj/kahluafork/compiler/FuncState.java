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

import org.luaj.kahluafork.compiler.LexState.ConsControl;
import org.luaj.kahluafork.compiler.LexState.expdesc;

import se.krka.kahlua.vm.LuaException;
import se.krka.kahlua.vm.LuaPrototype;

class FuncState {

    /**
     * use return values from previous op
     */
    public static final int LUA_MULTRET = -1;
    /**
     * masks for new-style vararg
     */
    public static final int VARARG_HASARG = 1;
    public static final int VARARG_ISVARARG = 2;
    public static final int VARARG_NEEDSARG = 4;
    /*----------------------------------------------------------------------
     name		args	description
     ------------------------------------------------------------------------*/
    public static final int OP_MOVE = 0;/*	A B	R(A) := R(B)					*/
    public static final int OP_LOADK = 1;/*	A Bx	R(A) := Kst(Bx)					*/
    public static final int OP_GETUPVAL = 4; /*	A B	R(A) := UpValue[B]				*/
    //	LTable h;  /* table to find (and reuse) elements in `k' */
    public static final int OP_SETTABLE = 9; /*	A B C	R(A)[RK(B)] := RK(C)				*/
    public static final int OP_NEWTABLE = 10; /*	A B C	R(A) := {} (size = B,C)				*/
    public static final int OP_CALL = 28; /*	A B C	R(A), ... ,R(A+C-2) := R(A)(R(A+1), ... ,R(A+B-1)) */
    public static final int OP_TAILCALL = 29; /*	A B C	return R(A)(R(A+1), ... ,R(A+B-1))		*/
    public static final int OP_FORLOOP = 31; /*	A sBx	R(A)+=R(A+2);
     if R(A) <?= R(A+1) then { pc+=sBx; R(A+3)=R(A) }*/
    public static final int OP_FORPREP = 32; /*	A sBx	R(A)-=R(A+2); pc+=sBx				*/
    public static final int OP_TFORLOOP = 33; /*	A C	R(A+3), ... ,R(A+2+C) := R(A)(R(A+1), R(A+2));
     if R(A+3) ~= nil then R(A+2)=R(A+3) else pc++	*/
    public static final int OP_CLOSE = 35; /*	A 	close all variables in the stack up to (>=) R(A)*/
    public static final int OP_CLOSURE = 36; /*	A Bx	R(A) := closure(KPROTO[Bx], R(A), ... ,R(A+n))	*/
    public static final int OP_VARARG = 37; /*	A B	R(A), R(A+1), ..., R(A+B-1) = vararg		*/
    static final int LUAI_MAXVARS = 200;
    private static final Object NULL_OBJECT = new Object();
    private static final int MAXSTACK = 250;
    private static final int LUAI_MAXUPVALUES = 60;
    /* OpArgMask */
    private static final int OpArgN = 0; /* argument is not used */
    private static final int OpArgU = 1; /* argument is used */
    private static final int OpArgR = 2; /* argument is a register or a jump offset */
    private static final int OpArgK = 3;   /* argument is a constant or register/constant */
    /*===========================================================================
     We assume that instructions are unsigned numbers.
     All instructions have an opcode in the first 6 bits.
     Instructions can have the following fields:
     `A' : 8 bits
     `B' : 9 bits
     `C' : 9 bits
     `Bx' : 18 bits (`B' and `C' together)
     `sBx' : signed Bx

     A signed argument is represented in excess K; that is, the number
     value is the unsigned value minus K. K is exactly the maximum value
     for that argument (so that -max is represented by 0, and +max is
     represented by 2*max), which is half the maximum for the corresponding
     unsigned argument.
     ===========================================================================*/
    /* basic instruction format */
    private static final int iABC = 0;
    private static final int iABx = 1;
    private static final int iAsBx = 2;
    /*
     ** size and position of opcode arguments.
     */
    private static final int SIZE_C = 9;
    private static final int SIZE_B = 9;
    private static final int SIZE_Bx = (SIZE_C + SIZE_B);
    private static final int SIZE_A = 8;
    private static final int SIZE_OP = 6;
    private static final int POS_OP = 0;
    private static final int POS_A = (POS_OP + SIZE_OP);
    private static final int POS_C = (POS_A + SIZE_A);
    private static final int POS_B = (POS_C + SIZE_C);
    private static final int POS_Bx = POS_C;
    private static final int MAX_OP = ((1 << SIZE_OP) - 1);
    private static final int MAXARG_A = ((1 << SIZE_A) - 1);
    private static final int MAXARG_B = ((1 << SIZE_B) - 1);
    private static final int MAXARG_C = ((1 << SIZE_C) - 1);
    private static final int MAXARG_Bx = ((1 << SIZE_Bx) - 1);
    private static final int MAXARG_sBx = (MAXARG_Bx >> 1);     	/* `sBx' is signed */
    private static final int MASK_OP = ((1 << SIZE_OP) - 1) << POS_OP;
    private static final int MASK_A = ((1 << SIZE_A) - 1) << POS_A;
    private static final int MASK_B = ((1 << SIZE_B) - 1) << POS_B;
    private static final int MASK_C = ((1 << SIZE_C) - 1) << POS_C;
    private static final int MASK_Bx = ((1 << SIZE_Bx) - 1) << POS_Bx;
    private static final int MASK_NOT_OP = ~MASK_OP;
    private static final int MASK_NOT_A = ~MASK_A;
    private static final int MASK_NOT_B = ~MASK_B;
    private static final int MASK_NOT_C = ~MASK_C;
    private static final int MASK_NOT_Bx = ~MASK_Bx;
    /**
     * this bit 1 means constant (0 means register)
     */
    private static final int BITRK = (1 << (SIZE_B - 1));
    private static final int MAXINDEXRK = (BITRK - 1);
    /**
     * * invalid register that fits in 8 bits
     */
    private static final int NO_REG = MAXARG_A;
    private static final int OP_LOADBOOL = 2;/*	A B C	R(A) := (Bool)B; if (C) pc++			*/
    private static final int OP_LOADNIL = 3; /*	A B	R(A) := ... := R(B) := nil			*/
    private static final int OP_GETGLOBAL = 5; /*	A Bx	R(A) := Gbl[Kst(Bx)]				*/
    private static final int OP_GETTABLE = 6; /*	A B C	R(A) := R(B)[RK(C)]				*/
    private static final int OP_SETGLOBAL = 7; /*	A Bx	Gbl[Kst(Bx)] := R(A)				*/
    private static final int OP_SETUPVAL = 8; /*	A B	UpValue[B] := R(A)				*/
    private static final int OP_SELF = 11; /*	A B C	R(A+1) := R(B); R(A) := R(B)[RK(C)]		*/
    private static final int OP_ADD = 12; /*	A B C	R(A) := RK(B) + RK(C)				*/
    private static final int OP_SUB = 13; /*	A B C	R(A) := RK(B) - RK(C)				*/
    private static final int OP_MUL = 14; /*	A B C	R(A) := RK(B) * RK(C)				*/
    private static final int OP_DIV = 15; /*	A B C	R(A) := RK(B) / RK(C)				*/
    private static final int OP_MOD = 16; /*	A B C	R(A) := RK(B) % RK(C)				*/
    private static final int OP_POW = 17; /*	A B C	R(A) := RK(B) ^ RK(C)				*/
    private static final int OP_UNM = 18; /*	A B	R(A) := -R(B)					*/
    private static final int OP_NOT = 19; /*	A B	R(A) := not R(B)				*/
    private static final int OP_LEN = 20; /*	A B	R(A) := length of R(B)				*/
    private static final int OP_CONCAT = 21; /*	A B C	R(A) := R(B).. ... ..R(C)			*/
    private static final int OP_JMP = 22; /*	sBx	pc+=sBx					*/
    private static final int OP_EQ = 23; /*	A B C	if ((RK(B) == RK(C)) ~= A) then pc++		*/
    private static final int OP_LT = 24; /*	A B C	if ((RK(B) <  RK(C)) ~= A) then pc++  		*/
    private static final int OP_LE = 25; /*	A B C	if ((RK(B) <= RK(C)) ~= A) then pc++  		*/
    private static final int OP_TEST = 26; /*	A C	if not (R(A) <=> C) then pc++			*/
    private static final int OP_TESTSET = 27; /*	A B C	if (R(B) <=> C) then R(A) := R(B) else pc++	*/
    private static final int OP_RETURN = 30; /*	A B	return R(A), ... ,R(A+B-2)	(see note)	*/
    private static final int OP_SETLIST = 34; /*	A B C	R(A)[(C-1)*FPF+i] := R(A+i), 1 <= i <= B	*/
    /*===========================================================================
     Notes:
     (*) In OP_CALL, if (B == 0) then B = top. C is the number of returns - 1,
     and can be 0: OP_CALL then sets `top' to last_result+1, so
     next open instruction (OP_CALL, OP_RETURN, OP_SETLIST) may use `top'.

     (*) In OP_VARARG, if (B == 0) then use actual number of varargs and
     set top (like in OP_CALL with C == 0).

     (*) In OP_RETURN, if (B == 0) then return up to `top'

     (*) In OP_SETLIST, if (B == 0) then B = `top';
     if (C == 0) then next `instruction' is real C

     (*) For comparisons, A specifies what condition the test should accept
     (true or false).

     (*) All `skips' (pc++) assume that next instruction is a jump
     ===========================================================================*/
    /*
     ** masks for instruction properties. The format is:
     ** bits 0-1: op mode
     ** bits 2-3: C arg mode
     ** bits 4-5: B arg mode
     ** bit 6: instruction set register A
     ** bit 7: operator is a test
     */
    private static final int[] luaP_opmodes = {
        /*   T        A           B             C          mode		   opcode	*/
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iABC), /* OP_MOVE */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgN << 2) | (iABx), /* OP_LOADK */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgU << 2) | (iABC), /* OP_LOADBOOL */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iABC), /* OP_LOADNIL */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgN << 2) | (iABC), /* OP_GETUPVAL */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgN << 2) | (iABx), /* OP_GETGLOBAL */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgK << 2) | (iABC), /* OP_GETTABLE */
            (0 << 7) | (0 << 6) | (OpArgK << 4) | (OpArgN << 2) | (iABx), /* OP_SETGLOBAL */
            (0 << 7) | (0 << 6) | (OpArgU << 4) | (OpArgN << 2) | (iABC), /* OP_SETUPVAL */
            (0 << 7) | (0 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_SETTABLE */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgU << 2) | (iABC), /* OP_NEWTABLE */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgK << 2) | (iABC), /* OP_SELF */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_ADD */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_SUB */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_MUL */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_DIV */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_MOD */
            (0 << 7) | (1 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_POW */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iABC), /* OP_UNM */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iABC), /* OP_NOT */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iABC), /* OP_LEN */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgR << 2) | (iABC), /* OP_CONCAT */
            (0 << 7) | (0 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iAsBx), /* OP_JMP */
            (1 << 7) | (0 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_EQ */
            (1 << 7) | (0 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_LT */
            (1 << 7) | (0 << 6) | (OpArgK << 4) | (OpArgK << 2) | (iABC), /* OP_LE */
            (1 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgU << 2) | (iABC), /* OP_TEST */
            (1 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgU << 2) | (iABC), /* OP_TESTSET */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgU << 2) | (iABC), /* OP_CALL */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgU << 2) | (iABC), /* OP_TAILCALL */
            (0 << 7) | (0 << 6) | (OpArgU << 4) | (OpArgN << 2) | (iABC), /* OP_RETURN */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iAsBx), /* OP_FORLOOP */
            (0 << 7) | (1 << 6) | (OpArgR << 4) | (OpArgN << 2) | (iAsBx), /* OP_FORPREP */
            (1 << 7) | (0 << 6) | (OpArgN << 4) | (OpArgU << 2) | (iABC), /* OP_TFORLOOP */
            (0 << 7) | (0 << 6) | (OpArgU << 4) | (OpArgU << 2) | (iABC), /* OP_SETLIST */
            (0 << 7) | (0 << 6) | (OpArgN << 4) | (OpArgN << 2) | (iABC), /* OP_CLOSE */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgN << 2) | (iABx), /* OP_CLOSURE */
            (0 << 7) | (1 << 6) | (OpArgU << 4) | (OpArgN << 2) | (iABC), /* OP_VARARG */};
    /* number of list items to accumulate before a SETLIST instruction */
    private static final int LFIELDS_PER_FLUSH = 50;
    final int[] upvalues_k = new int[LUAI_MAXUPVALUES];  /* upvalues */
    final int[] upvalues_info = new int[LUAI_MAXUPVALUES];  /* upvalues */
    final short[] actvar = new short[LUAI_MAXVARS];  /* declared-variable stack */
    /* information about local variables */
    public String[] locvars;
    /* upvalue names */
    public String[] upvalues;
    private final int linedefined;
    public int isVararg;
    final LuaPrototype f;  /* current function header */
    private final HashMap<Object, Integer> htable = new HashMap<>();  /* table to find (and reuse) elements in `k' */
    final FuncState prev;  /* enclosing function */
    private final LexState ls;  /* lexical state */
    BlockCnt bl = null;  /* chain of current blocks */
    int pc = 0;  /* next position to code (equivalent to `ncode') */
    private int lasttarget = -1;   /* `pc' of last `jump target' */
    private int jpc = LexState.NO_JUMP;  /* list of pending jumps to `pc' */
    int freereg = 0;  /* first free register */
    int nk = 0;  /* number of elements in `k' */
    int np = 0;  /* number of elements in `p' */
    int nlocvars = 0;  /* number of elements in `locvars' */
    int nactvar = 0;  /* number of active local variables */

    FuncState(String name, FuncState prev, LexState ls, int linedefined) {
        f = new LuaPrototype(name);
        this.prev = prev;
        this.ls = ls;
        this.linedefined = linedefined;
    }

    static void _assert(boolean b) {
        if (!b) {
            throw new LuaException("compiler assert failed");
        }
    }

    static void SET_OPCODE(InstructionPtr i, int o) {
        i.set((i.get() & (MASK_NOT_OP)) | ((o << POS_OP) & MASK_OP));
    }

    private static void SETARG_A(InstructionPtr i, int u) {
        i.set((i.get() & (MASK_NOT_A)) | ((u << POS_A) & MASK_A));
    }

    static void SETARG_B(InstructionPtr i, int u) {
        i.set((i.get() & (MASK_NOT_B)) | ((u << POS_B) & MASK_B));
    }

    static void SETARG_C(InstructionPtr i, int u) {
        i.set((i.get() & (MASK_NOT_C)) | ((u << POS_C) & MASK_C));
    }

    private static void SETARG_Bx(InstructionPtr i, int u) {
        i.set((i.get() & (MASK_NOT_Bx)) | ((u << POS_Bx) & MASK_Bx));
    }

    private static void SETARG_sBx(InstructionPtr i, int u) {
        SETARG_Bx(i, u + MAXARG_sBx);
    }

    private static int CREATE_ABC(int o, int a, int b, int c) {
        return ((o << POS_OP) & MASK_OP)
                | ((a << POS_A) & MASK_A)
                | ((b << POS_B) & MASK_B)
                | ((c << POS_C) & MASK_C);
    }

    private static int CREATE_ABx(int o, int a, int bc) {
        return ((o << POS_OP) & MASK_OP)
                | ((a << POS_A) & MASK_A)
                | ((bc << POS_Bx) & MASK_Bx);
    }

    // vector reallocation
    static Object[] realloc(Object[] v, int n) {
        Object[] a = new Object[n];
        if (v != null) {
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        }
        return a;
    }

    static String[] realloc(String[] v, int n) {
        String[] a = new String[n];
        if (v != null) {
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        }
        return a;
    }

    static LuaPrototype[] realloc(LuaPrototype[] v, int n) {
        LuaPrototype[] a = new LuaPrototype[n];
        if (v != null) {
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        }
        return a;
    }

    static int[] realloc(int[] v, int n) {
        int[] a = new int[n];
        if (v != null) {
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        }
        return a;
    }

    static byte[] realloc(byte[] v, int n) {
        byte[] a = new byte[n];
        if (v != null) {
            System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
        }
        return a;
    }

    // from lopcodes.h

    /*
     ** the following macros help to manipulate instructions
     */
    private static int GET_OPCODE(int i) {
        return (i >> POS_OP) & MAX_OP;
    }

    public static int GETARG_A(int i) {
        return (i >> POS_A) & MAXARG_A;
    }

    private static int GETARG_B(int i) {
        return (i >> POS_B) & MAXARG_B;
    }

    private static int GETARG_C(int i) {
        return (i >> POS_C) & MAXARG_C;
    }

    private static int GETARG_sBx(int i) {
        return ((i >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
    }

    /**
     * test whether value is a constant
     */
    private static boolean ISK(int x) {
        return 0 != ((x) & BITRK);
    }

    /**
     * code a constant index as a RK value
     */
    private static int RKASK(int x) {
        return ((x) | BITRK);
    }

    private static int getOpMode(int m) {
        return luaP_opmodes[m] & 3;
    }

    private static int getBMode(int m) {
        return (luaP_opmodes[m] >> 4) & 3;
    }

    private static int getCMode(int m) {
        return (luaP_opmodes[m] >> 2) & 3;
    }

    private static boolean testTMode(int m) {
        return 0 != (luaP_opmodes[m] & (1 << 7));
    }

    // =============================================================
    // from lcode.h
    // =============================================================
    InstructionPtr getcodePtr(expdesc e) {
        return new InstructionPtr(f.code, e.info);
    }

    int getcode(expdesc e) {
        return f.code[e.info];
    }

    int codeAsBx(int o, int A, int sBx) {
        return codeABx(o, A, sBx + MAXARG_sBx);
    }

    void setmultret(expdesc e) {
        setreturns(e, LUA_MULTRET);
    }

    // =============================================================
    // from lparser.c
    // =============================================================
    private String getlocvar(int i) {
        return locvars[actvar[i]];
    }

    void checklimit(int v, int l, String msg) {
        if (v > l) {
            errorlimit(l, msg);
        }
    }

    private void errorlimit(int limit, String what) {
        String msg = (linedefined == 0)
                ? "main function has more than " + limit + " " + what
                : "function at line " + linedefined + " has more than " + limit + " " + what;
        ls.lexerrorNotoken(msg);
    }

    private int indexupvalue(String name, expdesc v) {
        int i;
        for (i = 0; i < f.numUpvalues; i++) {
            if (upvalues_k[i] == v.k && upvalues_info[i] == v.info) {
                _assert(upvalues[i] == name);
                return i;
            }
        }
        /* new one */
        checklimit(f.numUpvalues + 1, LUAI_MAXUPVALUES, "upvalues");
        if (upvalues == null || f.numUpvalues + 1 > upvalues.length) {
            upvalues = realloc(upvalues, f.numUpvalues * 2 + 1);
        }
        upvalues[f.numUpvalues] = name;
        _assert(v.k == LexState.VLOCAL || v.k == LexState.VUPVAL);

        int numUpvalues = f.numUpvalues;
        f.numUpvalues++;
        upvalues_k[numUpvalues] = (byte) (v.k);
        upvalues_info[numUpvalues] = (byte) (v.info);
        return numUpvalues;
    }

    private int searchvar(String n) {
        int i;
        for (i = nactvar - 1; i >= 0; i--) {
            if (n == getlocvar(i)) {
                return i;
            }
        }
        return -1; /* not found */
    }

    private void markupval(int level) {
        BlockCnt bl = this.bl;
        while (bl != null && bl.nactvar > level) {
            bl = bl.previous;
        }
        if (bl != null) {
            bl.upval = true;
        }
    }

    int singlevaraux(String n, expdesc var, int base) {
        int v = searchvar(n); /* look up at current level */

        if (v >= 0) {
            var.init(LexState.VLOCAL, v);
            if (base == 0) {
                markupval(v); /* local will be used as an upval */
            }
            return LexState.VLOCAL;
        } else { /* not found at current level; try upper one */

            if (prev == null) { /* no more levels? */
                /* default is global variable */

                var.init(LexState.VGLOBAL, NO_REG);
                return LexState.VGLOBAL;
            }
            if (prev.singlevaraux(n, var, 0) == LexState.VGLOBAL) {
                return LexState.VGLOBAL;
            }
            var.info = indexupvalue(n, var); /* else was LOCAL or UPVAL */

            var.k = LexState.VUPVAL; /* upvalue in this level */

            return LexState.VUPVAL;
        }
    }

    void enterblock(BlockCnt bl, boolean isbreakable) {
        bl.breaklist = LexState.NO_JUMP;
        bl.isbreakable = isbreakable;
        bl.nactvar = this.nactvar;
        bl.upval = false;
        bl.previous = this.bl;
        this.bl = bl;
        _assert(this.freereg == this.nactvar);
    }

    void leaveblock() {
        BlockCnt bl = this.bl;
        this.bl = bl.previous;
        ls.removevars(bl.nactvar);
        if (bl.upval) {
            this.codeABC(OP_CLOSE, bl.nactvar, 0, 0);
        }
        /* a block either controls scope or breaks (never both) */
        _assert(!bl.isbreakable || !bl.upval);
        _assert(bl.nactvar == this.nactvar);
        this.freereg = this.nactvar; /* free registers */

        this.patchtohere(bl.breaklist);
    }

    void closelistfield(ConsControl cc) {
        if (cc.v.k == LexState.VVOID) {
            return; /* there is no list item */

        }
        this.exp2nextreg(cc.v);
        cc.v.k = LexState.VVOID;
        if (cc.tostore == LFIELDS_PER_FLUSH) {
            this.setlist(cc.t.info, cc.na, cc.tostore); /* flush */

            cc.tostore = 0; /* no more items pending */

        }
    }

    private boolean hasmultret(int k) {
        return ((k) == LexState.VCALL || (k) == LexState.VVARARG);
    }

    void lastlistfield(ConsControl cc) {
        if (cc.tostore == 0) {
            return;
        }
        if (hasmultret(cc.v.k)) {
            this.setmultret(cc.v);
            this.setlist(cc.t.info, cc.na, LUA_MULTRET);
            cc.na--;
            /**
             * do not count last expression (unknown number of elements)
             */
        } else {
            if (cc.v.k != LexState.VVOID) {
                this.exp2nextreg(cc.v);
            }
            this.setlist(cc.t.info, cc.na, cc.tostore);
        }
    }

    // =============================================================
    // from lcode.c
    // =============================================================
    void nil(int from, int n) {
        InstructionPtr previous;
        if (this.pc > this.lasttarget) { /* no jumps to current position? */

            if (this.pc == 0) { /* function start? */

                if (from >= this.nactvar) {
                    return; /* positions are already clean */

                }
            } else {
                previous = new InstructionPtr(this.f.code, this.pc - 1);
                if (GET_OPCODE(previous.get()) == OP_LOADNIL) {
                    int pfrom = GETARG_A(previous.get());
                    int pto = GETARG_B(previous.get());
                    if (pfrom <= from && from <= pto + 1) { /* can connect both? */

                        if (from + n - 1 > pto) {
                            SETARG_B(previous, from + n - 1);
                        }
                        return;
                    }
                }
            }
        }
        /* else no optimization */
        this.codeABC(OP_LOADNIL, from, from + n - 1, 0);
    }

    int jump() {
        int jpc = this.jpc; /* save list of jumps to here */

        this.jpc = LexState.NO_JUMP;
        int j = this.codeAsBx(OP_JMP, 0, LexState.NO_JUMP);
        j = this.concat(j, jpc); /* keep them on hold */

        return j;
    }

    void ret(int first, int nret) {
        this.codeABC(OP_RETURN, first, nret + 1, 0);
    }

    private int condjump(int /* OpCode */ op, int A, int B, int C) {
        this.codeABC(op, A, B, C);
        return this.jump();
    }

    private void fixjump(int pc, int dest) {
        InstructionPtr jmp = new InstructionPtr(this.f.code, pc);
        int offset = dest - (pc + 1);
        _assert(dest != LexState.NO_JUMP);
        if (Math.abs(offset) > MAXARG_sBx) {
            ls.syntaxerror("control structure too long");
        }
        SETARG_sBx(jmp, offset);
    }

    /*
     * * returns current `pc' and marks it as a jump target (to avoid wrong *
     * optimizations with consecutive instructions not in the same basic block).
     */
    int getlabel() {
        this.lasttarget = this.pc;
        return this.pc;
    }

    private int getjump(int pc) {
        int offset = GETARG_sBx(this.f.code[pc]);
        /* point to itself represents end of list */
        if (offset == LexState.NO_JUMP) /* end of list */ {
            return LexState.NO_JUMP;
        } else /* turn offset into absolute position */ {
            return (pc + 1) + offset;
        }
    }


    /*
     ** Macros to operate RK indices
     */

    private InstructionPtr getjumpcontrol(int pc) {
        InstructionPtr pi = new InstructionPtr(this.f.code, pc);
        if (pc >= 1 && testTMode(GET_OPCODE(pi.code[pi.idx - 1]))) {
            return new InstructionPtr(pi.code, pi.idx - 1);
        } else {
            return pi;
        }
    }

    /*
     * * check whether list has any jump that do not produce a value * (or
     * produce an inverted value)
     */
    private boolean need_value(int list) {
        for (; list != LexState.NO_JUMP; list = this.getjump(list)) {
            int i = this.getjumpcontrol(list).get();
            if (GET_OPCODE(i) != OP_TESTSET) {
                return true;
            }
        }
        return false; /* not found */

    }

    private boolean patchtestreg(int node, int reg) {
        InstructionPtr i = this.getjumpcontrol(node);
        if (GET_OPCODE(i.get()) != OP_TESTSET) /* cannot patch other instructions */ {
            return false;
        }
        if (reg != NO_REG && reg != GETARG_B(i.get())) {
            SETARG_A(i, reg);
        } else /* no register to put value or register already has the value */ {
            i.set(CREATE_ABC(OP_TEST, GETARG_B(i.get()), 0, GETARG_C(i.get())));
        }

        return true;
    }

    private void removevalues(int list) {
        for (; list != LexState.NO_JUMP; list = this.getjump(list)) {
            this.patchtestreg(list, NO_REG);
        }
    }

    private void patchlistaux(int list, int vtarget, int reg, int dtarget) {
        while (list != LexState.NO_JUMP) {
            int next = this.getjump(list);
            if (this.patchtestreg(list, reg)) {
                this.fixjump(list, vtarget);
            } else {
                this.fixjump(list, dtarget); /* jump to default target */

            }
            list = next;
        }
    }


    /*
     ** R(x) - register
     ** Kst(x) - constant (in constant table)
     ** RK(x) == if ISK(x) then Kst(INDEXK(x)) else R(x)
     */
    /*
     ** grep "ORDER OP" if you change these enums
     */

    private void dischargejpc() {
        this.patchlistaux(this.jpc, this.pc, NO_REG, this.pc);
        this.jpc = LexState.NO_JUMP;
    }

    void patchlist(int list, int target) {
        if (target == this.pc) {
            this.patchtohere(list);
        } else {
            _assert(target < this.pc);
            this.patchlistaux(list, target, NO_REG, target);
        }
    }

    void patchtohere(int list) {
        this.getlabel();
        this.jpc = this.concat(this.jpc, list);
    }

    int concat(int l1, int l2) {
        if (l2 == LexState.NO_JUMP) {
            return l1;
        }
        if (l1 == LexState.NO_JUMP) {
            l1 = l2;
        } else {
            int list = l1;
            int next;
            while ((next = this.getjump(list)) != LexState.NO_JUMP) /* find last element */ {
                list = next;
            }
            this.fixjump(list, l2);
        }
        return l1;
    }

    void checkstack(int n) {
        int newstack = this.freereg + n;
        if (newstack > this.f.maxStacksize) {
            if (newstack >= MAXSTACK) {
                ls.syntaxerror("function or expression too complex");
            }
            this.f.maxStacksize = newstack;
        }
    }

    void reserveregs(int n) {
        this.checkstack(n);
        this.freereg += n;
    }

    private void freereg(int reg) {
        if (!ISK(reg) && reg >= this.nactvar) {
            this.freereg--;
            _assert(reg == this.freereg);
        }
    }

    private void freeexp(expdesc e) {
        if (e.k == LexState.VNONRELOC) {
            this.freereg(e.info);
        }
    }

    private int addk(Object v) {
        int idx;
        if (this.htable.containsKey(v)) {
            idx = htable.get(v);
        } else {
            idx = this.nk;
            this.htable.put(v, idx);
            final LuaPrototype f = this.f;
            if (f.constants == null || nk + 1 >= f.constants.length) {
                f.constants = realloc(f.constants, nk * 2 + 1);
            }
            if (v == NULL_OBJECT) {
                v = null;
            }
            f.constants[this.nk++] = v;
        }
        return idx;
    }

    int stringK(String s) {
        return this.addk(s);
    }

    int numberK(double r) {
        return this.addk(r);
    }

    private int boolK(boolean b) {
        return this.addk((b ? Boolean.TRUE : Boolean.FALSE));
    }

    private int nilK() {
        return this.addk(NULL_OBJECT);
    }

    void setreturns(expdesc e, int nresults) {
        if (e.k == LexState.VCALL) { /* expression is an open function call? */

            SETARG_C(this.getcodePtr(e), nresults + 1);
        } else if (e.k == LexState.VVARARG) {
            SETARG_B(this.getcodePtr(e), nresults + 1);
            SETARG_A(this.getcodePtr(e), this.freereg);
            this.reserveregs(1);
        }
    }

    void setoneret(expdesc e) {
        if (e.k == LexState.VCALL) { /* expression is an open function call? */

            e.k = LexState.VNONRELOC;
            e.info = GETARG_A(this.getcode(e));
        } else if (e.k == LexState.VVARARG) {
            SETARG_B(this.getcodePtr(e), 2);
            e.k = LexState.VRELOCABLE; /* can relocate its simple result */

        }
    }

    void dischargevars(expdesc e) {
        switch (e.k) {
            case LexState.VLOCAL: {
                e.k = LexState.VNONRELOC;
                break;
            }
            case LexState.VUPVAL: {
                e.info = this.codeABC(OP_GETUPVAL, 0, e.info, 0);
                e.k = LexState.VRELOCABLE;
                break;
            }
            case LexState.VGLOBAL: {
                e.info = this.codeABx(OP_GETGLOBAL, 0, e.info);
                e.k = LexState.VRELOCABLE;
                break;
            }
            case LexState.VINDEXED: {
                this.freereg(e.aux);
                this.freereg(e.info);
                e.info = this
                        .codeABC(OP_GETTABLE, 0, e.info, e.aux);
                e.k = LexState.VRELOCABLE;
                break;
            }
            case LexState.VVARARG:
            case LexState.VCALL: {
                this.setoneret(e);
                break;
            }
            default:
                break; /* there is one value available (somewhere) */

        }
    }

    private int code_label(int A, int b, int jump) {
        this.getlabel(); /* those instructions may be jump targets */

        return this.codeABC(OP_LOADBOOL, A, b, jump);
    }

    private void discharge2reg(expdesc e, int reg) {
        this.dischargevars(e);
        switch (e.k) {
            case LexState.VNIL: {
                this.nil(reg, 1);
                break;
            }
            case LexState.VFALSE:
            case LexState.VTRUE: {
                this.codeABC(OP_LOADBOOL, reg, (e.k == LexState.VTRUE ? 1 : 0),
                        0);
                break;
            }
            case LexState.VK: {
                this.codeABx(OP_LOADK, reg, e.info);
                break;
            }
            case LexState.VKNUM: {
                this.codeABx(OP_LOADK, reg, this.numberK(e.nval()));
                break;
            }
            case LexState.VRELOCABLE: {
                InstructionPtr pc = this.getcodePtr(e);
                SETARG_A(pc, reg);
                break;
            }
            case LexState.VNONRELOC: {
                if (reg != e.info) {
                    this.codeABC(OP_MOVE, reg, e.info, 0);
                }
                break;
            }
            default: {
                _assert(e.k == LexState.VVOID || e.k == LexState.VJMP);
                return; /* nothing to do... */

            }
        }
        e.info = reg;
        e.k = LexState.VNONRELOC;
    }

    private void discharge2anyreg(expdesc e) {
        if (e.k != LexState.VNONRELOC) {
            this.reserveregs(1);
            this.discharge2reg(e, this.freereg - 1);
        }
    }

    private void exp2reg(expdesc e, int reg) {
        this.discharge2reg(e, reg);
        if (e.k == LexState.VJMP) {
            e.t = this.concat(e.t, e.info); /* put this jump in `t' list */

        }
        if (e.hasjumps()) {
            int _final; /* position after whole expression */

            int p_f = LexState.NO_JUMP; /* position of an eventual LOAD false */

            int p_t = LexState.NO_JUMP; /* position of an eventual LOAD true */

            if (this.need_value(e.t) || this.need_value(e.f)) {
                int fj = (e.k == LexState.VJMP) ? LexState.NO_JUMP : this
                        .jump();
                p_f = this.code_label(reg, 0, 1);
                p_t = this.code_label(reg, 1, 0);
                this.patchtohere(fj);
            }
            _final = this.getlabel();
            this.patchlistaux(e.f, _final, reg, p_f);
            this.patchlistaux(e.t, _final, reg, p_t);
        }
        e.f = e.t = LexState.NO_JUMP;
        e.info = reg;
        e.k = LexState.VNONRELOC;
    }

    void exp2nextreg(expdesc e) {
        this.dischargevars(e);
        this.freeexp(e);
        this.reserveregs(1);
        this.exp2reg(e, this.freereg - 1);
    }

    int exp2anyreg(expdesc e) {
        this.dischargevars(e);
        if (e.k == LexState.VNONRELOC) {
            if (!e.hasjumps()) {
                return e.info; /* exp is already in a register */

            }
            if (e.info >= this.nactvar) { /* reg. is not a local? */

                this.exp2reg(e, e.info); /* put value on it */

                return e.info;
            }
        }
        this.exp2nextreg(e); /* default */

        return e.info;
    }

    void exp2val(expdesc e) {
        if (e.hasjumps()) {
            this.exp2anyreg(e);
        } else {
            this.dischargevars(e);
        }
    }

    int exp2RK(expdesc e) {
        this.exp2val(e);
        switch (e.k) {
            case LexState.VKNUM:
            case LexState.VTRUE:
            case LexState.VFALSE:
            case LexState.VNIL: {
                if (this.nk <= MAXINDEXRK) { /* constant fit in RK operand? */

                    e.info = (e.k == LexState.VNIL) ? this.nilK()
                            : (e.k == LexState.VKNUM) ? this.numberK(e.nval())
                            : this.boolK((e.k == LexState.VTRUE));
                    e.k = LexState.VK;
                    return RKASK(e.info);
                } else {
                    break;
                }
            }
            case LexState.VK: {
                if (e.info <= MAXINDEXRK) /* constant fit in argC? */ {
                    return RKASK(e.info);
                } else {
                    break;
                }
            }
            default:
                break;
        }
        /* not a constant in the right range: put it in a register */
        return this.exp2anyreg(e);
    }

    void storevar(expdesc var, expdesc ex) {
        switch (var.k) {
            case LexState.VLOCAL: {
                this.freeexp(ex);
                this.exp2reg(ex, var.info);
                return;
            }
            case LexState.VUPVAL: {
                int e = this.exp2anyreg(ex);
                this.codeABC(OP_SETUPVAL, e, var.info, 0);
                break;
            }
            case LexState.VGLOBAL: {
                int e = this.exp2anyreg(ex);
                this.codeABx(OP_SETGLOBAL, e, var.info);
                break;
            }
            case LexState.VINDEXED: {
                int e = this.exp2RK(ex);
                this.codeABC(OP_SETTABLE, var.info, var.aux, e);
                break;
            }
            default: {
                _assert(false); /* invalid var kind to store */

                break;
            }
        }
        this.freeexp(ex);
    }

    void self(expdesc e, expdesc key) {
        int func;
        this.exp2anyreg(e);
        this.freeexp(e);
        func = this.freereg;
        this.reserveregs(2);
        this.codeABC(OP_SELF, func, e.info, this.exp2RK(key));
        this.freeexp(key);
        e.info = func;
        e.k = LexState.VNONRELOC;
    }

    private void invertjump(expdesc e) {
        InstructionPtr pc = this.getjumpcontrol(e.info);
        _assert(testTMode(GET_OPCODE(pc.get()))
                && GET_OPCODE(pc.get()) != OP_TESTSET && GET_OPCODE(pc.get()) != OP_TEST);
        // SETARG_A(pc, !(GETARG_A(pc.get())));
        int a = GETARG_A(pc.get());
        int nota = (a != 0 ? 0 : 1);
        SETARG_A(pc, nota);
    }

    private int jumponcond(expdesc e, int cond) {
        if (e.k == LexState.VRELOCABLE) {
            int ie = this.getcode(e);
            if (GET_OPCODE(ie) == OP_NOT) {
                this.pc--; /* remove previous OP_NOT */

                return this.condjump(OP_TEST, GETARG_B(ie), 0, (cond != 0 ? 0 : 1));
            }
            /* else go through */
        }
        this.discharge2anyreg(e);
        this.freeexp(e);
        return this.condjump(OP_TESTSET, NO_REG, e.info, cond);
    }

    void goiftrue(expdesc e) {
        int pc; /* pc of last jump */

        this.dischargevars(e);
        switch (e.k) {
            case LexState.VK:
            case LexState.VKNUM:
            case LexState.VTRUE: {
                pc = LexState.NO_JUMP; /* always true; do nothing */

                break;
            }
            case LexState.VFALSE: {
                pc = this.jump(); /* always jump */

                break;
            }
            case LexState.VJMP: {
                this.invertjump(e);
                pc = e.info;
                break;
            }
            default: {
                pc = this.jumponcond(e, 0);
                break;
            }
        }
        e.f = this.concat(e.f, pc); /* insert last jump in `f' list */

        this.patchtohere(e.t);
        e.t = LexState.NO_JUMP;
    }

    private void goiffalse(expdesc e) {
        int pc; /* pc of last jump */

        this.dischargevars(e);
        switch (e.k) {
            case LexState.VNIL:
            case LexState.VFALSE: {
                pc = LexState.NO_JUMP; /* always false; do nothing */

                break;
            }
            case LexState.VTRUE: {
                pc = this.jump(); /* always jump */

                break;
            }
            case LexState.VJMP: {
                pc = e.info;
                break;
            }
            default: {
                pc = this.jumponcond(e, 1);
                break;
            }
        }
        e.t = this.concat(e.t, pc); /* insert last jump in `t' list */

        this.patchtohere(e.f);
        e.f = LexState.NO_JUMP;
    }

    private void codenot(expdesc e) {
        this.dischargevars(e);
        switch (e.k) {
            case LexState.VNIL:
            case LexState.VFALSE: {
                e.k = LexState.VTRUE;
                break;
            }
            case LexState.VK:
            case LexState.VKNUM:
            case LexState.VTRUE: {
                e.k = LexState.VFALSE;
                break;
            }
            case LexState.VJMP: {
                this.invertjump(e);
                break;
            }
            case LexState.VRELOCABLE:
            case LexState.VNONRELOC: {
                this.discharge2anyreg(e);
                this.freeexp(e);
                e.info = this.codeABC(OP_NOT, 0, e.info, 0);
                e.k = LexState.VRELOCABLE;
                break;
            }
            default: {
                _assert(false); /* cannot happen */

                break;
            }
        }
        /* interchange true and false lists */
        {
            int temp = e.f;
            e.f = e.t;
            e.t = temp;
        }
        this.removevalues(e.f);
        this.removevalues(e.t);
    }

    void indexed(expdesc t, expdesc k) {
        t.aux = this.exp2RK(k);
        t.k = LexState.VINDEXED;
    }

    private boolean constfolding(int op, expdesc e1, expdesc e2) {
        if (!e1.isnumeral() || !e2.isnumeral()) {
            return false;
        }
        double v1, v2, r;
        v1 = e1.nval();
        v2 = e2.nval();
        switch (op) {
            case OP_ADD:
                r = v1 + v2;
                break;
            case OP_SUB:
                r = v1 - v2;
                break;
            case OP_MUL:
                r = v1 * v2;
                break;
            case OP_DIV:
                r = v1 / v2;
                break;
            case OP_MOD:
                r = v1 % v2;
                break;
            case OP_POW:
                return false;
            case OP_UNM:
                r = -v1;
                break;
            case OP_LEN:
                return false; /* no constant folding for 'len' */

            default:
                _assert(false);
                return false;
        }
        if (Double.isNaN(r) || Double.isInfinite(r)) {
            return false; /* do not attempt to produce NaN */

        }
        e1.setNval(r);
        return true;
    }

    private void codearith(int op, expdesc e1, expdesc e2) {
        if (!constfolding(op, e1, e2)) {
            int o2 = (op != OP_UNM && op != OP_LEN) ? this.exp2RK(e2) : 0;
            int o1 = this.exp2RK(e1);
            if (o1 > o2) {
                this.freeexp(e1);
                this.freeexp(e2);
            } else {
                this.freeexp(e2);
                this.freeexp(e1);
            }
            e1.info = this.codeABC(op, 0, o1, o2);
            e1.k = LexState.VRELOCABLE;
        }
    }

    private void codecomp(int /* OpCode */ op, int cond, expdesc e1, expdesc e2) {
        int o1 = this.exp2RK(e1);
        int o2 = this.exp2RK(e2);
        this.freeexp(e2);
        this.freeexp(e1);
        if (cond == 0 && op != OP_EQ) {
            int temp; /* exchange args to replace by `<' or `<=' */

            temp = o1;
            o1 = o2;
            o2 = temp; /* o1 <==> o2 */

            cond = 1;
        }
        e1.info = this.condjump(op, cond, o1, o2);
        e1.k = LexState.VJMP;
    }

    void prefix(int /* UnOpr */ op, expdesc e) {
        expdesc e2 = new expdesc();
        e2.init(LexState.VKNUM, 0);
        switch (op) {
            case LexState.OPR_MINUS: {
                if (e.k == LexState.VK) {
                    this.exp2anyreg(e); /* cannot operate on non-numeric constants */

                }
                this.codearith(OP_UNM, e, e2);
                break;
            }
            case LexState.OPR_NOT:
                this.codenot(e);
                break;
            case LexState.OPR_LEN: {
                this.exp2anyreg(e); /* cannot operate on constants */

                this.codearith(OP_LEN, e, e2);
                break;
            }
            default:
                _assert(false);
        }
    }

    void infix(int /* BinOpr */ op, expdesc v) {
        switch (op) {
            case LexState.OPR_AND: {
                this.goiftrue(v);
                break;
            }
            case LexState.OPR_OR: {
                this.goiffalse(v);
                break;
            }
            case LexState.OPR_CONCAT: {
                this.exp2nextreg(v); /* operand must be on the `stack' */

                break;
            }
            case LexState.OPR_ADD:
            case LexState.OPR_SUB:
            case LexState.OPR_MUL:
            case LexState.OPR_DIV:
            case LexState.OPR_MOD:
            case LexState.OPR_POW: {
                if (!v.isnumeral()) {
                    this.exp2RK(v);
                }
                break;
            }
            default: {
                this.exp2RK(v);
                break;
            }
        }
    }

    void posfix(int op, expdesc e1, expdesc e2) {
        switch (op) {
            case LexState.OPR_AND: {
                _assert(e1.t == LexState.NO_JUMP); /* list must be closed */

                this.dischargevars(e2);
                e2.f = this.concat(e2.f, e1.f);
                // *e1 = *e2;
                e1.setvalue(e2);
                break;
            }
            case LexState.OPR_OR: {
                _assert(e1.f == LexState.NO_JUMP); /* list must be closed */

                this.dischargevars(e2);
                e2.t = this.concat(e2.t, e1.t);
                // *e1 = *e2;
                e1.setvalue(e2);
                break;
            }
            case LexState.OPR_CONCAT: {
                this.exp2val(e2);
                if (e2.k == LexState.VRELOCABLE
                        && GET_OPCODE(this.getcode(e2)) == OP_CONCAT) {
                    _assert(e1.info == GETARG_B(this.getcode(e2)) - 1);
                    this.freeexp(e1);
                    SETARG_B(this.getcodePtr(e2), e1.info);
                    e1.k = LexState.VRELOCABLE;
                    e1.info = e2.info;
                } else {
                    this.exp2nextreg(e2); /* operand must be on the 'stack' */

                    this.codearith(OP_CONCAT, e1, e2);
                }
                break;
            }
            case LexState.OPR_ADD:
                this.codearith(OP_ADD, e1, e2);
                break;
            case LexState.OPR_SUB:
                this.codearith(OP_SUB, e1, e2);
                break;
            case LexState.OPR_MUL:
                this.codearith(OP_MUL, e1, e2);
                break;
            case LexState.OPR_DIV:
                this.codearith(OP_DIV, e1, e2);
                break;
            case LexState.OPR_MOD:
                this.codearith(OP_MOD, e1, e2);
                break;
            case LexState.OPR_POW:
                this.codearith(OP_POW, e1, e2);
                break;
            case LexState.OPR_EQ:
                this.codecomp(OP_EQ, 1, e1, e2);
                break;
            case LexState.OPR_NE:
                this.codecomp(OP_EQ, 0, e1, e2);
                break;
            case LexState.OPR_LT:
                this.codecomp(OP_LT, 1, e1, e2);
                break;
            case LexState.OPR_LE:
                this.codecomp(OP_LE, 1, e1, e2);
                break;
            case LexState.OPR_GT:
                this.codecomp(OP_LT, 0, e1, e2);
                break;
            case LexState.OPR_GE:
                this.codecomp(OP_LE, 0, e1, e2);
                break;
            default:
                _assert(false);
        }
    }

    void fixline(int line) {
        this.f.lines[this.pc - 1] = line;
    }

    private int code(int instruction, int line) {
        LuaPrototype f = this.f;
        this.dischargejpc(); /* `pc' will change */
        /* put new instruction in code array */

        if (f.code == null || this.pc + 1 > f.code.length) {
            f.code = realloc(f.code, this.pc * 2 + 1);
        }
        f.code[this.pc] = instruction;
        /* save corresponding line information */
        if (f.lines == null || this.pc + 1 > f.lines.length) {
            f.lines = realloc(f.lines,
                    this.pc * 2 + 1);
        }
        f.lines[this.pc] = line;
        return this.pc++;
    }

    int codeABC(int o, int a, int b, int c) {
        _assert(getOpMode(o) == iABC);
        _assert(getBMode(o) != OpArgN || b == 0);
        _assert(getCMode(o) != OpArgN || c == 0);
        return this.code(CREATE_ABC(o, a, b, c), this.ls.baseLexer.lastline);
    }

    int codeABx(int o, int a, int bc) {
        _assert(getOpMode(o) == iABx || getOpMode(o) == iAsBx);
        _assert(getCMode(o) == OpArgN);
        return this.code(CREATE_ABx(o, a, bc), this.ls.baseLexer.lastline);
    }

    private void setlist(int base, int nelems, int tostore) {
        int c = (nelems - 1) / LFIELDS_PER_FLUSH + 1;
        int b = (tostore == LUA_MULTRET) ? 0 : tostore;
        _assert(tostore != 0);
        if (c <= MAXARG_C) {
            this.codeABC(OP_SETLIST, base, b, c);
        } else {
            this.codeABC(OP_SETLIST, base, b, 0);
            this.code(c, this.ls.baseLexer.lastline);
        }
        this.freereg = base + 1; /* free registers with list values */

    }

    static class BlockCnt {
        BlockCnt previous;  /* chain */

        int breaklist;  /* list of jumps out of this loop */

        int nactvar;  /* # active locals outside the breakable structure */

        boolean upval;  /* true if some variable in the block is an upvalue */

        boolean isbreakable;  /* true if `block' is a loop */
    }
}
