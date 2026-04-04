package ctrmap.beaterscript;

public class Token {
    private TokenType type;
    private SubTokenType subtype;
    private Object value;
    
    public Token(TokenType type, SubTokenType subtype, Object value) {
        this.type = type;
        this.subtype = subtype;
        this.value = value;
    }
    
    public TokenType getType() {
        return this.type;
    }   
    
    public SubTokenType getSubType() {
        return this.subtype;
    }    
    
    public Object getValue() {
        return this.value;
    }
}
