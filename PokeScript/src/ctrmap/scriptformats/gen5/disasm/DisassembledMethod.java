package ctrmap.scriptformats.gen5.disasm;

import java.util.ArrayList;
import java.util.List;

public class DisassembledMethod {
	public boolean isPublic = false;
	public int ptr;
	
	public List<DisassembledCall> instructions = new ArrayList<>();
	
	public DisassembledMethod(int ptr){
		this.ptr = ptr;
	}
	
	public String getName(){
		String name = "";
		if (instructions.isEmpty()) {
			return "nullsub_" + Integer.toHexString(ptr);
		}                  
                for (DisassembledCall c : instructions) {
                    if (c.labels != null && c.labels.size() > 0) {
                        return c.labels.get(0);
                    } else if (c.labels == null) {
                        return "method_" + Integer.toHexString(ptr);
                    }
                }
		return name;
	}
}
