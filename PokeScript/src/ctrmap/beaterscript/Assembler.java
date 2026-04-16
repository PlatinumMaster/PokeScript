package ctrmap.beaterscript;

import static ctrmap.pokescript.LangCompiler.langCompilerArgConfig;
import ctrmap.pokescript.instructions.ntr.NTRArgument;
import ctrmap.pokescript.instructions.ntr.NTRDataType;
import ctrmap.scriptformats.gen5.VCommandDataBase;
import ctrmap.scriptformats.gen5.VCommandDataBase.VCommand;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xstandard.cli.ArgumentBuilder;
import xstandard.cli.ArgumentContent;
import xstandard.fs.FSFile;
import xstandard.fs.accessors.DiskFile;
import xstandard.io.base.impl.ext.data.DataIOStream;

public class Assembler {
    public static void Assemble(FSFile script, FSFile out, VCommandDataBase cdb) throws UnsupportedEncodingException, IOException {
        DataIOStream outputStream = out.getDataIOStream();
        
        // Tokenize the script, and get all of the tokens for processing.
        ArrayList<Token> tokens = Tokenizer.Tokenize(script);
            
        // Pass 1: Create a mapping of all labels and their addresses.
        // Also, verify the commands to ensure that they are valid.
        long current_command_section_address = 0;
        long current_line_number = 1;
        long header_size = 2;
        
        HashMap<String, Long> label_to_address_map = new HashMap<String, Long>();
        ArrayList<String> main_functions = new ArrayList<>();
        for (int TokenIndex = 0; TokenIndex < tokens.size(); ) {
            Token token = tokens.get(TokenIndex);
            TokenType tt = token.getType();
            if (tt == TokenType.LABEL) {
                label_to_address_map.put((String) token.getValue(), current_command_section_address);
                
                if (token.getSubType() == SubTokenType.LABEL_MAIN) {
                    header_size += 4;
                    main_functions.add((String) token.getValue());
                }
                TokenIndex++;
            } else if (tt == TokenType.OPCODE) {
                TokenIndex++;
                
                // TODO: Include enhanced BSP commands here.
                String command_name = (String) token.getValue();
                if (command_name.equals("action")) {
                    // TODO: Verify actions.
                    
                    // If the command exists, go through and validate the parameters.
                    if (TokenIndex + 2 > tokens.size()) {
                        // Error; arguments wrong!
                        System.out.println(String.format("[Line %d] Error: missing arguments!", current_line_number));
                        return;
                    }
                    
                    // Count the arguments after this opcode.
                    int ArgumentCount = 0;
                    Token current_token = tokens.get(TokenIndex);
                    while (current_token.getType() == TokenType.ARGUMENT) {
                        ArgumentCount++;
                        current_token = tokens.get(++TokenIndex);
                    }
                    
                    if (ArgumentCount != 2) {
                        // Error; unknown data between arguments!
                        System.out.println(String.format("[Line %d] Error: unknown data between arguments!", current_line_number));
                        return;
                    }
                    
                } else {
                    // Check if the command exists. If not, then stop (impossible to iterate).
                    VCommand cmd = cdb.getCommandProtoByName(command_name);
                    if (cmd == null) {
                        // Error; command not found in current database!
                        System.out.println(String.format("[Line %d] Error: command \"%s\" not found!", current_line_number, command_name));
                        return;
                    }

                    // If the command exists, go through and validate the parameters.
                    if (TokenIndex + cmd.def.parameters.length > tokens.size()) {
                        // Error; arguments wrong!
                        System.out.println(String.format("[Line %d] Error: missing arguments!", current_line_number));
                        return;
                    }

                    // Count the arguments after this opcode.
                    int ArgumentCount = 0;
                    for (int ArgumentIndex = 0; ArgumentIndex < cmd.def.parameters.length; ++ArgumentIndex) {
                        // TODO: Verify parameter types (do it in the if statement)
                        Token t = tokens.get(TokenIndex + ArgumentIndex);
                        if (t.getType() == TokenType.ARGUMENT) {
                            ArgumentCount++;
                        } else { 
                            // Error; unknown data between arguments!
                            System.out.println(String.format("[Line %d] Error: unknown data between arguments!", current_line_number));
                            return;
                        }
                    }

                    if (ArgumentCount != cmd.def.parameters.length) {
                        // Error; arguments wrong!
                        System.out.println(String.format("[Line %d] Error: expected more arguments than got!", current_line_number));
                        return;
                    }

                    TokenIndex += cmd.def.parameters.length;
                    current_command_section_address += cmd.def.getSize();
                }
            } else if (tt == TokenType.NEWLINE) {
                current_line_number++;
                TokenIndex++;
            }
        }
        
        // Fixup the label addresses (we now need to account for the header section).
        for (Map.Entry<String, Long> entry : label_to_address_map.entrySet()) {
            entry.setValue(entry.getValue() + header_size);
        }
        
        // Write the main labels (and, stop bytes) for header.
        // TODO: add a way to determine if we should use the stop bytes or not.
        
        long current_header_address = 0;
        for (String main_label : main_functions) {
            int address_absolute = (int)(long)label_to_address_map.get(main_label);
            int address_relative = (int) (address_absolute - current_header_address - 4);
            outputStream.writeInt(address_relative);
            current_header_address += 4;
        }
        
        outputStream.writeShort(0xFD13);
        
        // Pass 2: Go through the tokens, and serialize each of the command tokens.
        current_command_section_address = header_size;
        current_line_number = 1;
        
        for (int TokenIndex = 0; TokenIndex < tokens.size(); ) {
            Token token = tokens.get(TokenIndex);
            TokenType tt = token.getType();
            if (tt == TokenType.OPCODE) {
                ++TokenIndex;
                // Get the command metadata.
                String command_name = (String) token.getValue();
                if (command_name.equals("action")) {
                    // TODO: Verify actions.
                    
                    // If the command exists, go through and validate the parameters.
                    if (TokenIndex + 2 > tokens.size()) {
                        // Error; arguments wrong!
                        System.out.println(String.format("[Line %d] Error: missing arguments!", current_line_number));
                        return;
                    }
                    
                    // Count the arguments after this opcode.
                    for (int ArgumentIndex = 0; ArgumentIndex < 2; ++ArgumentIndex) {
                        Token subtoken = tokens.get(TokenIndex + ArgumentIndex);
                        SubTokenType stt = subtoken.getSubType();
                        Object value = subtoken.getValue();
                        if (stt == SubTokenType.IMMEDIATE) {
                            outputStream.writeShort((Integer)value & 0xFFFF);
                            current_command_section_address += 2;
                        } else {
                            // Error; arguments wrong!
                            System.out.println(String.format("[Line %d] Error: expected a valid type here", current_line_number));
                            return;
                        }
                    }
                    
                    TokenIndex += 2;
                } else {
                    VCommand cmd = cdb.getCommandProtoByName(command_name);

                    // Write the raw opcode.
                    outputStream.writeShort(cmd.def.opCode & 0xFFFF);  

                    // Count the arguments after this opcode.
                    for (int ArgumentIndex = 0; ArgumentIndex < cmd.def.parameters.length; ++ArgumentIndex) {
                        Token subtoken = tokens.get(TokenIndex + ArgumentIndex);
                        SubTokenType stt = subtoken.getSubType();
                        Object value = subtoken.getValue();
                        NTRArgument arg_meta = cmd.def.parameters[ArgumentIndex];
                        if (stt == SubTokenType.IMMEDIATE) {
                            // Immediate; get the type from the command parameters, and serialize it.
                            if (arg_meta.dataType == NTRDataType.U8) {
                                outputStream.writeByte((Integer)value & 0xFF);
                                current_command_section_address += 1;
                            } else if (arg_meta.dataType == NTRDataType.U16 || arg_meta.dataType == NTRDataType.VAR || arg_meta.dataType == NTRDataType.FLEX || arg_meta.dataType == NTRDataType.FX16) {
                                outputStream.writeShort((Integer)value & 0xFFFF);
                                current_command_section_address += 2;
                            } else if (arg_meta.dataType == NTRDataType.S32 || arg_meta.dataType == NTRDataType.FX32) {
                                outputStream.writeInt((Integer)value & 0xFFFFFFFF);  
                                current_command_section_address += 4;
                            } 
                        } else if (stt == SubTokenType.IMMEDIATE_FX) {
                            if (arg_meta.dataType == NTRDataType.FX16) {
                                outputStream.writeShort((int) ((Double)value * 4096));  
                                current_command_section_address += 2;
                            } else if (arg_meta.dataType == NTRDataType.FX32) {
                                outputStream.writeInt((int) ((Double)value * 4096));  
                                current_command_section_address += 4;
                            }
                        } else if (stt == SubTokenType.LABEL_SUB && label_to_address_map.containsKey(value)) {
                            // Calculate the jump.
                            long target_address = label_to_address_map.get(value);
                            outputStream.writeInt((int) (target_address + 4 - current_command_section_address));
                            current_command_section_address += 4;
                        } else {
                            // Error; arguments wrong!
                            System.out.println(String.format("[Line %d] Error: expected a valid type here", current_line_number));
                            return;
                        }
                    }
                    TokenIndex += cmd.def.parameters.length;
                }
                
            } else if (tt == TokenType.NEWLINE) {
                current_line_number++;
                TokenIndex++;
            } else if (tt == TokenType.LABEL || tt == TokenType.COMMENT) {
                TokenIndex++;
            } else {
                // Error; arguments wrong!
                System.out.println(String.format("[Line %d] SOMETING WONG", current_line_number));
                return;
            }
        }
        
        outputStream.close();
    }
}
