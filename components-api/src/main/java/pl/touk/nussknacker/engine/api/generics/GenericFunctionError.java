package pl.touk.nussknacker.engine.api.generics;

public class GenericFunctionError implements SpelParseError {
    private String message;

    public GenericFunctionError(String message_) {
        message = message_;
    }

    @Override
    public String message() {
        return message;
    }
}
