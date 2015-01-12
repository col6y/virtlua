/*
 Copyright (c) 2007-2009 Kristofer Karlsson <kristofer.karlsson@gmail.com>

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

import java.util.Random;

import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm.LuaState;
import se.krka.kahlua.vm.LuaTable;

public enum MathLib implements JavaFunction {

    // Generic math functions
    ABS {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.abs(x));
                }
            },
    CEIL {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.ceil(x));
                }
            },
    FLOOR {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.floor(x));
                }
            },
    MODF {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);

                    boolean negate = false;
                    if (MathLib.isNegative(x)) {
                        negate = true;
                        x = -x;
                    }
                    double intPart = Math.floor(x);
                    double fracPart;
                    if (Double.isInfinite(intPart)) {
                        fracPart = 0;
                    } else {
                        fracPart = x - intPart;
                    }
                    if (negate) {
                        intPart = -intPart;
                        fracPart = -fracPart;
                    }
                    return callFrame.push(intPart, fracPart);
                }
            },
    FMOD {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 2, "Not enough arguments");
                    double v1 = getDoubleArg(callFrame, 1);
                    double v2 = getDoubleArg(callFrame, 2);

                    double res;
                    if (Double.isInfinite(v1) || Double.isNaN(v1)) {
                        res = Double.NaN;
                    } else if (Double.isInfinite(v2)) {
                        res = v1;
                    } else {
                        v2 = Math.abs(v2);
                        boolean negate = false;
                        if (MathLib.isNegative(v1)) {
                            negate = true;
                            v1 = -v1;
                        }
                        res = v1 - Math.floor(v1 / v2) * v2;
                        if (negate) {
                            res = -res;
                        }
                    }
                    return callFrame.push(res);
                }
            },
    RANDOM {
                // Random functions
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    Random random = callFrame.thread.state.random;
                    if (nArguments == 0) {
                        return callFrame.push(random.nextDouble());
                    }

                    double tmp = getDoubleArg(callFrame, 1);
                    int m = (int) tmp;
                    int n;
                    if (nArguments == 1) {
                        n = m;
                        m = 1;
                    } else {
                        tmp = getDoubleArg(callFrame, 2);
                        n = (int) tmp;
                    }
                    return callFrame.push((double) (m + random.nextInt(n - m + 1)));
                }
            },
    RANDOMSEED {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    Object o = callFrame.get(0);
                    if (o != null) {
                        callFrame.thread.state.random.setSeed(o.hashCode());
                    }
                    return 0;
                }
            },
    COSH {
                // Hyperbolic functions
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);

                    double exp_x = exp(x);
                    return callFrame.push((exp_x + 1 / exp_x) * 0.5);
                }
            },
    SINH {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);

                    double exp_x = exp(x);
                    return callFrame.push((exp_x - 1 / exp_x) * 0.5);
                }
            },
    TANH {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);

                    double exp_x = exp(2 * x);
                    return callFrame.push((exp_x - 1) / (exp_x + 1));
                }
            },
    DEG {
                // Trig functions
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.toDegrees(x));
                }
            },
    RAD {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.toRadians(x));
                }
            },
    ACOS {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(acos(x));
                }
            },
    ASIN {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(asin(x));
                }
            },
    ATAN {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(atan(x));
                }
            },
    ATAN2 {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 2, "Not enough arguments");
                    double y = getDoubleArg(callFrame, 1);
                    double x = getDoubleArg(callFrame, 2);
                    return callFrame.push(atan2(y, x));
                }
            },
    COS {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.cos(x));
                }
            },
    SIN {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.sin(x));
                }
            },
    TAN {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.tan(x));
                }
            },
    // Power functions
    SQRT {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(Math.sqrt(x));
                }
            },
    EXP {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(exp(x));
                }
            },
    POW {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 2, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    double y = getDoubleArg(callFrame, 2);
                    return callFrame.push(pow(x, y));
                }
            },
    LOG {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(ln(x));
                }
            },
    LOG10 {

                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);
                    return callFrame.push(ln(x) * LN10_INV);
                }
            },
    FREXP {

                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 1, "Not enough arguments");
                    double x = getDoubleArg(callFrame, 1);

                    double e, m;
                    if (Double.isInfinite(x) || Double.isNaN(x)) {
                        e = 0;
                        m = x;
                    } else {
                        e = Math.ceil(ln(x) * LN2_INV);
                        int div = 1 << ((int) e);
                        m = x / div;
                    }
                    return callFrame.push(m, e);
                }
            },
    LDEXP {
                @Override
                public int call(LuaCallFrame callFrame, int nArguments) {
                    BaseLib.luaAssert(nArguments >= 2, "Not enough arguments");
                    double m = getDoubleArg(callFrame, 1);
                    double dE = getDoubleArg(callFrame, 2);

                    double ret;
                    double tmp = m + dE;
                    if (Double.isInfinite(tmp) || Double.isNaN(tmp)) {
                        ret = m;
                    } else {
                        int e = (int) dE;
                        ret = m * (1 << e);
                    }

                    return callFrame.push(ret);
                }
            };

    public String getName() {
        return this.name().toLowerCase();
    }

    public static void register(LuaState state) {
        LuaTable math = new LuaTable();
        state.getEnvironment().rawset("math", math);

        math.rawset("pi", Math.PI);
        math.rawset("huge", Double.POSITIVE_INFINITY);

        for (MathLib f : MathLib.values()) {
            math.rawset(f.getName(), f);
        }
    }

    @Override
    public String toString() {
        return "math." + getName();
    }

    protected double getDoubleArg(LuaCallFrame callFrame, int n) {
        return (Double) BaseLib.getArg(callFrame, n, BaseLib.TYPE_NUMBER, getName());
    }

    public static boolean isNegative(double vDouble) {
        return Double.doubleToLongBits(vDouble) < 0;
    }

    // Rounds towards even numbers
    public static double round(double x) {
        if (x < 0) {
            return -round(-x);
        }
        x += 0.5;
        double x2 = Math.floor(x);
        if (x2 == x) {
            return x2 - ((long) x2 & 1);
        }
        return x2;
    }

    /**
     * Rounds to keep <em>precision</em> decimals. Rounds towards even numbers.
     *
     * @param x the number to round
     * @param precision the precision to round to. A precision of 3 will for
     * instance round 1.65432 to 1.654
     * @return the rounded number
     */
    public static double roundToPrecision(double x, int precision) {
        double roundingOffset = MathLib.ipow(10, precision);
        return round(x * roundingOffset) / roundingOffset;
    }

    public static double roundToSignificantNumbers(double x, int precision) {
        if (x == 0) {
            return 0;
        }
        if (x < 0) {
            return -roundToSignificantNumbers(-x, precision);
        }
        double lowerLimit = MathLib.ipow(10.0, precision - 1);
        double upperLimit = lowerLimit * 10.0;
        double multiplier = 1.0;
        while (multiplier * x < lowerLimit) {
            multiplier *= 10.0;
        }
        while (multiplier * x >= upperLimit) {
            multiplier /= 10.0;
        }
        return round(x * multiplier) / multiplier;
    }

    private static final double LN10_INV = 1 / ln(10);
    private static final double LN2_INV = 1 / ln(2);
    private static final double EPS = 1e-15;

    /*
     * Simple implementation of the taylor expansion of
     * exp(x) = 1 + x + x^2/2 + x^3/6 + ...
     */
    public static double exp(double x) {
        double x_acc = 1;
        double div = 1;

        double res = 0;
        while (Math.abs(x_acc) > EPS) {
            res = res + x_acc;

            x_acc *= x;
            x_acc /= div;
            div++;
        }
        return res;
    }

    /*
     * Simple implementation of the taylor expansion of
     * ln(1 - t) = t - t^2/2 -t^3/3 - ... - t^n/n + ...
     */
    public static double ln(double x) {
        boolean negative = false;

        if (x < 0) {
            return Double.NaN;
        }
        if (x == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (Double.isInfinite(x)) {
            return Double.POSITIVE_INFINITY;
        }
        if (x < 1) {
            negative = true;
            x = 1 / x;
        }
        int multiplier = 1;

        // x must be between 0 and 2 - close to 1 means faster taylor expansion
        while (x >= 1.1) {
            multiplier *= 2;
            x = Math.sqrt(x);
        }
        double t = 1 - x;
        double tpow = t;
        int divisor = 1;
        double result = 0;

        double toSubtract;
        while (Math.abs((toSubtract = tpow / divisor)) > EPS) {
            result -= toSubtract;
            tpow *= t;
            divisor++;
        }
        double res = multiplier * result;
        if (negative) {
            return -res;
        }
        return res;
    }

    public static double pow(double base, double exponent) {
        if ((int) exponent == exponent) {
            return ipow(base, (int) exponent);
        } else {
            return fpow(base, exponent);
        }
    }

    private static double fpow(double base, double exponent) {
        if (base < 0) {
            return Double.NaN;
        }
        return exp(exponent * ln(base));
    }

    /* Thanks rici lake for ipow-implementation */
    public static double ipow(double base, int exponent) {
        boolean inverse = false;
        if (MathLib.isNegative(exponent)) {
            exponent = -exponent;
            inverse = true;
        }
        double b;
        for (b = (exponent & 1) != 0 ? base : 1, exponent >>= 1; exponent != 0; exponent >>= 1) {
            base *= base;
            if ((exponent & 1) != 0) {
                b *= base;
            }
        }
        if (inverse) {
            return 1 / b;
        }
        return b;
    }

    // constants
    static final double sq2p1 = 2.414213562373095048802e0;
    static final double sq2m1 = .414213562373095048802e0;
    static final double p4 = .161536412982230228262e2;
    static final double p3 = .26842548195503973794141e3;
    static final double p2 = .11530293515404850115428136e4;
    static final double p1 = .178040631643319697105464587e4;
    static final double p0 = .89678597403663861959987488e3;
    static final double q4 = .5895697050844462222791e2;
    static final double q3 = .536265374031215315104235e3;
    static final double q2 = .16667838148816337184521798e4;
    static final double q1 = .207933497444540981287275926e4;
    static final double q0 = .89678597403663861962481162e3;
    static final double PIO2 = 1.5707963267948966135E0;

    // reduce
    private static double mxatan(double arg) {
        double argsq, value;

        argsq = arg * arg;
        value = ((((p4 * argsq + p3) * argsq + p2) * argsq + p1) * argsq + p0);
        value = value / (((((argsq + q4) * argsq + q3) * argsq + q2) * argsq + q1) * argsq + q0);
        return value * arg;
    }

    // reduce
    private static double msatan(double arg) {
        if (arg < sq2m1) {
            return mxatan(arg);
        }
        if (arg > sq2p1) {
            return PIO2 - mxatan(1 / arg);
        }
        return PIO2 / 2 + mxatan((arg - 1) / (arg + 1));
    }

    // implementation of atan
    public static double atan(double arg) {
        if (arg > 0) {
            return msatan(arg);
        }
        return -msatan(-arg);
    }

    // implementation of atan2
    public static double atan2(double arg1, double arg2) {
        // both are 0 or arg1 is +/- inf
        if (arg1 + arg2 == arg1) {
            if (arg1 > 0) {
                return PIO2;
            }
            if (arg1 < 0) {
                return -PIO2;
            }
            return 0;
        }
        arg1 = atan(arg1 / arg2);
        if (arg2 < 0) {
            if (arg1 <= 0) {
                return arg1 + Math.PI;
            }
            return arg1 - Math.PI;
        }
        return arg1;
    }

    // implementation of asin
    public static double asin(double arg) {
        double temp;
        int sign;

        sign = 0;
        if (arg < 0) {
            arg = -arg;
            sign++;
        }
        if (arg > 1) {
            return Double.NaN;
        }
        temp = Math.sqrt(1 - arg * arg);
        if (arg > 0.7) {
            temp = PIO2 - atan(temp / arg);
        } else {
            temp = atan(arg / temp);
        }
        if (sign > 0) {
            temp = -temp;
        }
        return temp;
    }

    // implementation of acos
    public static double acos(double arg) {
        if (arg > 1 || arg < -1) {
            return Double.NaN;
        }
        return PIO2 - asin(arg);

    }
}
