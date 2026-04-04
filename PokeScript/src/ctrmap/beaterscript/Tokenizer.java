package ctrmap.beaterscript;

import ctrmap.scriptformats.gen5.VCommandDataBase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import xstandard.fs.FSFile;

public class Tokenizer {
    public static boolean isInteger(String str) { 
        try {  
          Integer.parseInt(str);  
          return true;
        } catch (NumberFormatException e) {  
          return false;  
        }  
    }
    
    public static boolean isDouble(String str) { 
        try {  
          Double.parseDouble(str);  
          return true;
        } catch (NumberFormatException e) {  
          return false;  
        }  
    }
    
    public static ArrayList<Token> Tokenize(FSFile file) throws UnsupportedEncodingException, IOException {
        // Read the file.
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream().getInputStream(), "UTF-8"));
        
        // Read each line in the file, and tokenize it accordingly.
        ArrayList<Token> tokens = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String line_cleaned = line.trim();
            if (line_cleaned.matches("([^:]+):")){
                // Label; assume the data before the colon is the label.
                String label = line_cleaned.substring(0, line_cleaned.indexOf(':'));
                tokens.add(new Token(TokenType.LABEL, label.startsWith("main_") ? SubTokenType.LABEL_MAIN : SubTokenType.LABEL_SUB, label));
            } else if (line_cleaned.matches("# ([^:]+)")) {
                // Comment; parse.
                // TODO: These really don't need to be in the tokens.
                tokens.add(new Token(TokenType.COMMENT, SubTokenType.NONE, line_cleaned.substring(1).trim()));
            } else {
                // Opcode; tokenize it and each of the data.

                // If there data, then parse it.
                if (!line_cleaned.isEmpty()) {
                    // Go through the line, and read the data. Assume the format <opcode> [<arg1>, <arg2>, ...]
                    String[] line_tokens = line_cleaned.split("\\s*,\\s*|\\s+");
                    tokens.add(new Token(TokenType.OPCODE, SubTokenType.NONE, line_tokens[0]));
                    for (int TokenIndex = 1; TokenIndex < line_tokens.length; ++TokenIndex) {
                        String line_token = line_tokens[TokenIndex];
                        Object token_value;
                        SubTokenType stt = SubTokenType.NONE;

                        // Determine the subtype of the argument.
                        if (Tokenizer.isInteger(line_token)) {
                            stt = SubTokenType.IMMEDIATE;
                            token_value = Integer.parseInt(line_token);
                        } else if (Tokenizer.isDouble(line_token)) {
                            stt = SubTokenType.IMMEDIATE_FX;
                            token_value = Double.parseDouble(line_token);
                        } else {
                            // Treat as label.
                            stt = SubTokenType.LABEL_SUB;
                            token_value = line_token;
                        }

                        // Make a new token.
                        tokens.add(new Token(TokenType.ARGUMENT, stt, token_value));
                    }
                }
            }
            
            // Keep track of the newlines (for determining line numbers).
            tokens.add(new Token(TokenType.NEWLINE, SubTokenType.NONE, null));
        }

        return tokens;
    }
}
