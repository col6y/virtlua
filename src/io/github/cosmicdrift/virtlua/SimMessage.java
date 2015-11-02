/*
 Copyright (c) 2014-2015 Colby Skeggs

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
package io.github.cosmicdrift.virtlua;

public final class SimMessage {
    private final Object[] params;
    
    public SimMessage(Object... params) {
        if (params == null || params.length < 1 || params[0] == null) {
            throw new IllegalArgumentException("SimMessage requires at least one parameter!");
        }
        for (Object o : params) {
            if (o != null && !(o instanceof String) && !(o instanceof Double) && !(o instanceof Boolean)) {
                throw new IllegalArgumentException("SimMessage can only take null, strings, doubles, and booleans!");
            }
        }
        this.params = params;
    }
    
    public int length() {
        return params.length;
    }
    
    public Object get(int i) {
        return i < params.length ? params[i] : null;
    }
}
