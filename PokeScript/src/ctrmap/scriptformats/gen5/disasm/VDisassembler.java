package ctrmap.scriptformats.gen5.disasm;

import ctrmap.pokescript.instructions.ntr.NTRArgument;
import ctrmap.pokescript.instructions.ntr.NTRDataType;
import ctrmap.pokescript.instructions.ntr.NTRInstructionCall;
import ctrmap.pokescript.instructions.ntr.NTRInstructionPrototype;
import ctrmap.scriptformats.gen5.VCommandDataBase;
import ctrmap.scriptformats.gen5.VCommandDataBase.CommandType;
import ctrmap.scriptformats.gen5.VScriptFile;
import java.io.ByteArrayOutputStream;
import xstandard.fs.accessors.DiskFile;
import xstandard.io.base.impl.ext.data.DataIOStream;
import xstandard.text.FormattingUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import xstandard.io.util.IndentedPrintStream;

public class VDisassembler {

	private final VScriptFile scr;
	private final VCommandDataBase cdb;

	private List<LinkPrototype> publics = new ArrayList<>();
	private List<LinkPrototype> jumpLinks = new ArrayList<>();
	private List<LinkPrototype> functionCalls = new ArrayList<>();
	private List<LinkPrototype> actionJumps = new ArrayList<>();

	public List<DisassembledMethod> methods = new ArrayList<>();
	public List<DisassembledMethod> actionSequences = new ArrayList<>();

	public boolean unsafeMode = true;

	public VDisassembler(VScriptFile scr, VCommandDataBase cdb) {
		this.scr = scr;
		this.cdb = cdb;
	}

	public void disassemble() {
		if (scr.getSourceFile() != null) {
			System.out.println("Disassembling file " + scr.getSourceFile() + " with database command maximum 0x" + Integer.toHexString(cdb.getCommandMax()));
			try {
				DataIOStream dis = scr.getSourceFile().getDataIOStream();

				readScriptHeader(dis);

				for (LinkPrototype p : publics) {
					System.out.println("Reading public " + Integer.toHexString(p.targetOffset));
					DisassembledMethod m = readMethod(dis, p);
					if (m != null) {
						m.isPublic = true;
					}
				}

				dis.close();
			} catch (IOException ex) {
				Logger.getLogger(VScriptFile.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		createLabelsAndLinks();
	}

	public void commitToScript() {
		scr.instructions.clear();
		scr.publics.clear();
		for (DisassembledMethod m : methods) {
			if (m.isPublic) {
				scr.addPublic(m.instructions.get(0));
			}
			scr.instructions.addAll(m.instructions);
		}
		scr.updateLinks();
	}

	public boolean isJumpLabelUsed(int ptr) {
		for (LinkPrototype j : jumpLinks) {
			if (j.targetOffset == ptr) {
				return true;
			}
		}
		return false;
	}

	private DisassembledMethod findMethodByPtr(int ptr) {
		for (DisassembledMethod m : methods) {
			if (m.ptr == ptr) {
				return m;
			}
		}
		return null;
	}

	private DisassembledMethod findActionSeqByPtr(int ptr) {
		for (DisassembledMethod m : actionSequences) {
			if (m.ptr == ptr) {
				return m;
			}
		}
		return null;
	}

	private void createLabelsAndLinks() {
		System.out.println("--Re-linking main methods--");
		createLabelsImpl(publics, "main_", true, 1, true);
		System.out.println("--Re-linking sub methods--");
		createLabelsImpl(functionCalls, "sub_", false, 0, false);
		System.out.println("--Re-linking jumps--");
		createLabelsImpl(jumpLinks, "LABEL_", false, 0, false);
		System.out.println("--Re-linking actions--");
		createLabelsImpl(actionJumps, "ActionSequence_", false, 0, false);
	}

	private void createLabelsImpl(List<LinkPrototype> links, String prefix, boolean labelByIndex, int initialIndex, boolean arePublics) {
		int index = initialIndex;
		for (LinkPrototype jmp : links) {
			DisassembledCall jumpTarget = findCallOrActionSeqByPtr(jmp.targetOffset);
			DisassembledCall jumpSrc = findCallByPtr(jmp.sourceOffset);
			if (jumpTarget != null) {
                                if (!arePublics) {
                                    if (jumpSrc != null) {
                                            System.out.println("Setting up link from " + Integer.toHexString(jumpSrc.pointer) + " to " + prefix + Integer.toHexString(jumpTarget.pointer));
                                            jumpSrc.setupLinkToCall(jumpTarget);
                                    } else {
                                            System.out.println("SOURCE ABSENT from " + Integer.toHexString(jmp.sourceOffset) + " to " + jmp.targetOffset);
                                    }
                                }
                                
				if (jumpTarget.labels == null) {
                                    jumpTarget.labels = new ArrayList<>();
                                }
                                if (labelByIndex) {
                                    String new_label = prefix + index;
                                    if (!jumpTarget.labels.contains(new_label)) {
                                        jumpTarget.labels.add(new_label);
                                    }
                                } else {
                                    String new_label = prefix + FormattingUtils.getStrWithLeadingZeros(4, Integer.toHexString(jumpTarget.pointer));
                                    if (!jumpTarget.labels.contains(new_label)) {
                                        jumpTarget.labels.add(new_label);
                                    }
                                }
			} else {
				System.out.println("Could not link!! from " + Integer.toHexString(jmp.sourceOffset) + " to " + Integer.toHexString(jmp.targetOffset));
			}
			index++;
		}
	}

	private DisassembledCall findCallByPtr(int ptr) {
		return findCall(methods, ptr);
	}

	private DisassembledCall findCallOrActionSeqByPtr(int ptr) {
		List<DisassembledMethod> l = new ArrayList<>(methods);
		l.addAll(actionSequences);
		return findCall(l, ptr);
	}

	private static DisassembledCall findCall(List<DisassembledMethod> l, int ptr) {
		for (DisassembledMethod m : l) {
			for (DisassembledCall c : m.instructions) {
				int diff = ptr - c.pointer;
				if (diff >= 0 && diff < c.getSize()) {
					return c;
				}
			}
		}
		return null;
	}
	
	public static void main(String[] args) {
		try {
			DataIOStream io = new DiskFile("D:\\_REWorkspace\\pokescript_genv\\6\\6_1244.bin").getDataIOStream();
			
			int pos = 0;
			int len = io.getLength();
			
			while (pos + 4 < len) {
				io.seek(pos);
				int val = io.readInt();
				if (io.getPosition() + val == 0x35f) {
					System.out.println("fdf");
				}
				pos++;
			}
			
			io.close();
		} catch (IOException ex) {
			Logger.getLogger(VDisassembler.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private DisassembledMethod readMethod(DataIOStream dis, LinkPrototype lp) throws IOException {
		if (findMethodByPtr(lp.targetOffset) != null) {
			return null;
		}
		System.out.println("Reading method at " + Integer.toHexString(lp.targetOffset));
		int methodPtr = lp.targetOffset;
		dis.seek(methodPtr);
		int max = dis.getLength() - 2;

		DisassembledMethod m = new DisassembledMethod(methodPtr);
		List<LinkPrototype> localJumpLinks = new ArrayList<>();
		List<LinkPrototype> localCallLinks = new ArrayList<>();

		FuncReader:
		while (dis.getPosition() < max) {
			int insPtr = dis.getPosition();

			int opCode = dis.readUnsignedShort();
			//System.out.println("read " + Integer.toHexString(opCode) + " at " + Integer.toHexString(insPtr));

			VCommandDataBase.VCommand c = cdb.getCommandProto(opCode);
			if (c == null) {
				String text = "WARN: Could not detect command 0x" + Integer.toHexString(opCode) + " at " + Integer.toHexString(insPtr);
				if (unsafeMode) {
					System.out.println(text);
				} else {
					throw new RuntimeException(text);
				}
				if (!m.instructions.isEmpty()) {
					DisassembledCall lastCall = m.instructions.get(m.instructions.size() - 1);
					System.out.println("Last command: " + (lastCall.command != null ? lastCall.command.name : "[UNDETECTED]") + " at " + Integer.toHexString(lastCall.pointer) + "(size " + Integer.toHexString(lastCall.getSize()) + ")");
				}
				if ((opCode & 0xF000) != 0) {
					System.out.println("Suspicious command. Aligning stream.");
					dis.seek(dis.getPosition() - 1);
				}
			} else {
				int[] arguments = new int[c.def.parameters.length];

				for (int i = 0; i < arguments.length; i++) {
					int value = 0;

					switch (c.def.parameters[i].dataType) {
						case FLEX:
						case U16:
						case VAR:
							value = dis.readUnsignedShort();
							break;
						case FX16:
							value = dis.readShort();
							break;
						case FX32:
						case S32:
							value = dis.readInt();
							break;
						case U8:
							value = dis.readUnsignedByte();
							break;
					}

					arguments[i] = value;
				}

				DisassembledCall call = new DisassembledCall(insPtr, c.def, arguments);
				call.command = c;

				m.instructions.add(call);
				

				switch (c.type) {
					case HALT:
					case RETURN:
						//boolean hasJumpTo = false;
						LinkPrototype closestLinkJump = null;
						int closestLinkJumpPtr = Integer.MAX_VALUE;
						int pos = dis.getPosition();
						for (LinkPrototype jumpTo : localJumpLinks) {
							if (jumpTo.targetOffset >= pos) {
								if (jumpTo.targetOffset < closestLinkJumpPtr) {
									closestLinkJumpPtr = jumpTo.targetOffset;
									closestLinkJump = jumpTo;
								}
							}
						}
						if (closestLinkJump != null) {
							//WORKAROUND: If there is a forward jump to this instruction, keep reading
							dis.seek(closestLinkJumpPtr);
							break;
						}
						break FuncReader;
					case JUMP: {
						LinkPrototype link = new LinkPrototype(dis, call.args[call.args.length - 1]);
						jumpLinks.add(link);
						localJumpLinks.add(link);
						break;
					}
					case ACTION_JUMP:
						actionJumps.add(new LinkPrototype(dis, call.args[call.args.length - 1]));
						break;
					case CALL: {
						if (!call.command.callsExtern) {
							LinkPrototype link = new LinkPrototype(dis, call.args[call.args.length - 1]);
							System.out.println("Function call to " + Integer.toHexString(link.targetOffset) + " from " + Integer.toHexString(link.sourceOffset));
							localCallLinks.add(link);
						}
						break;
					}
				}
				//System.out.println(c.name + ": " + Arrays.toString(call.args));
			}
		}

		System.out.println("Method end at " + Integer.toHexString(dis.getPosition()));
		methods.add(m);

		for (LinkPrototype actionSeqCall : actionJumps) {
			if (findActionSeqByPtr(actionSeqCall.targetOffset) == null) {
				readActionSeq(dis, actionSeqCall);
			}
		}
		
		functionCalls.addAll(localCallLinks);

		for (LinkPrototype funcCall : localCallLinks) {
			if (findMethodByPtr(funcCall.targetOffset) == null) {
				readMethod(dis, funcCall);
			}
		}
		
		return m;
	}

	private void readActionSeq(DataIOStream dis, LinkPrototype lp) throws IOException {
		System.out.println("Reading action sequence at " + Integer.toHexString(lp.targetOffset) + "(from " + Integer.toHexString(lp.sourceOffset) + ")");
		int methodPtr = lp.targetOffset;
		dis.seek(methodPtr);
		int max = dis.getLength() - 4;

		DisassembledMethod m = new DisassembledMethod(methodPtr);

		ActionReader:
		while (dis.getPosition() < max) {
			int insPtr = dis.getPosition();

			int opCode = dis.readUnsignedShort();
			int duration = dis.readUnsignedShort();

			if (opCode != 0xFE) {
				m.instructions.add(new DisassembledCall(insPtr, new NTRInstructionPrototype(opCode, new NTRArgument(NTRDataType.U16)), duration));
			} else {
				break;
			}
		}

		actionSequences.add(m);
	}

	private void readScriptHeader(DataIOStream dis) throws IOException {
		dis.seek(0);
		int max = dis.getLength() - 4;

		while (dis.getPosition() < max) {
			int offset = dis.readInt();
			if ((offset & 0xFFFF) == VScriptFile.V_SCR_MAGIC) {
				return;
			}
			publics.add(new LinkPrototype(dis, offset));
		}
	}

	public void sortMethods() {
		Comparator<DisassembledMethod> comp = new Comparator<DisassembledMethod>() {
			@Override
			public int compare(DisassembledMethod o1, DisassembledMethod o2) {
				return o1.ptr - o2.ptr;
			}
		};

		List<DisassembledMethod> publicMethods = new ArrayList<>();
		List<DisassembledMethod> subMethods = new ArrayList<>();
		for (DisassembledMethod m : methods) {
			if (m.isPublic) {
				publicMethods.add(m);
			} else {
				subMethods.add(m);
			}
		}

		subMethods.sort(comp);
		methods.clear();
		methods.addAll(publicMethods);
		methods.addAll(subMethods);
		actionSequences.sort(comp);
	}
        
        public String dump() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IndentedPrintStream out = new IndentedPrintStream(baos);

		for (DisassembledMethod m : this.methods) {
                    dumpMethod(m, out);
		}
		for (DisassembledMethod m : this.actionSequences) {
			dumpActionSeq(m, out);
		}

		out.decrementIndentLevel();

		out.close();
		return new String(baos.toByteArray());
	}
        
        private void dumpMethod(DisassembledMethod m, IndentedPrintStream out) {
                out.incrementIndentLevel();
                
		for (DisassembledCall instruction : m.instructions) {    
                    if (instruction.labels != null) {
                        out.decrementIndentLevel();
                        for (String label : instruction.labels) {
                            out.println(String.format("%s:", label));
                        }
                        out.incrementIndentLevel();
                    }

                    String name = instruction.command.name;
                    if (name != null) {                    
                        StringBuilder sb = new StringBuilder();
                        sb.append(name);
                        sb.append(' ');
                        for (int Index = 0; Index < instruction.args.length; ++Index) {
                            if (instruction.command.type == CommandType.ACTION_JUMP || instruction.command.type == CommandType.CALL || instruction.command.type == CommandType.JUMP) {
                                if (instruction.definition.parameters[Index].dataType == NTRDataType.S32) {  
                                    boolean label_found = false;
                                    for (DisassembledMethod method : methods) {
                                        if (instruction.link != null && instruction.link.target.pointer == method.ptr) {
                                            sb.append(method.getName());
                                            label_found = true;
                                            break;
                                        } else {
                                            for (DisassembledCall c : method.instructions) {   
                                                if (instruction.link != null && c.labels != null && instruction.link.target.pointer == c.pointer) {
                                                    sb.append(c.labels.get(0));
                                                    label_found = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (!label_found) {
                                        sb.append(instruction.args[Index]);
                                        break;
                                    }
                                } else {
                                    sb.append(instruction.args[Index]);
                                }
                            } else {
                                sb.append(instruction.args[Index]);
                            }
                            if (Index < instruction.args.length - 1) {
                                sb.append(", ");
                            }
                        }
                        out.println(sb.toString());
                    } else {
                        out.println("label for instruction is null????");
                    }
		}
                
                out.decrementIndentLevel();

		out.println();
	}
        
        private void dumpActionSeq(DisassembledMethod m, IndentedPrintStream out) {
            out.println(String.format("%s:", m.getName()));
            out.incrementIndentLevel();

            for (DisassembledCall call : m.instructions) {
                    out.println(String.format("action %d, %d", call.definition.opCode, call.args[0]));
            }
            
            out.println(String.format("action %d, %d", 0xFE, 0x0));
            out.decrementIndentLevel();

            out.println();
	}
}
