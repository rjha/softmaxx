
package online.softmaxx.xapi.service.error;


/**
 * Encapsulates malformed JSON request line and column to 
 * eliminate primitive JSON parsing bugs.
 */
public record JsonCoordinate(int line, int column) {
    
    public JsonCoordinate {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("wrong line and column in JSON parser.");
        }
    }


    public String toDisplayString() {
        return String.format(" JSON parser error at [line: %d, column: %d]", line, column);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
