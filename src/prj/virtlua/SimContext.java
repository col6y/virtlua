package prj.virtlua;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.LuaPrototype;
import se.krka.kahlua.vm.LuaState;

public class SimContext {
    public final LinkedBlockingQueue<SimMessage> toSim;
    public final LinkedBlockingQueue<SimMessage> fromSim;
    private LuaState state;
    private final LuaPrototype bios;
    private boolean crashed = false;
    
    public SimContext(String bios, LinkedBlockingQueue<SimMessage> toSim, LinkedBlockingQueue<SimMessage> fromSim) throws IOException {
        this.bios = LuaCompiler.compilestring(bios, "<bios>");
        this.toSim = toSim;
        this.fromSim = fromSim;
    }
    
    public SimContext(String rootCode) throws IOException {
        this(rootCode, new LinkedBlockingQueue<SimMessage>(), new LinkedBlockingQueue<SimMessage>());
    }
    
    public void post(SimMessage message) {
        toSim.add(message);
    }
    
    public SimMessage poll() {
        return fromSim.poll();
    }
    
    public boolean isCrashed() {
        return crashed;
    }
    
    public boolean simulate(int ticks) { // returns true if there's currently more to process (should resume sooner rather than later) or false if not (can wait a bit)
        if (crashed) {
            return false;
        }
        try {
            if (state == null) {
                state = new LuaState();
                state.startCall(bios);
            }
            if (state.continueCall(ticks)) {
                state.startCall(bios); // the code returned - go back in on the next round ... after we pause.
                return false;
            } else {
                return true; // more to do
            }
        } catch (Throwable thr) {
            thr.printStackTrace();
            crashed = true;
            return false;
        }
    }
    
    public void hardReset() {
        state = null;
        fromSim.clear();
        toSim.clear();
        crashed = false;
    }
}
