package prj.virtlua;

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
        return params[i];
    }
}
