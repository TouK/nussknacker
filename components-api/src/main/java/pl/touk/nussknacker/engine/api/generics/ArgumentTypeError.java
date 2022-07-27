package pl.touk.nussknacker.engine.api.generics;

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import scala.collection.JavaConverters;

public abstract class ArgumentTypeError implements SpelParseError {
    public abstract List<TypingResult> found();

    public abstract String functionName();

    public abstract String expectedString();

    protected String typesToString(List<TypingResult> types) {
        List<String> typesDisplayed = types
                .stream()
                .map(TypingResult::display)
                .collect(Collectors.toList());
        return String.join(", ", typesDisplayed);
    }

    protected String typesToString(scala.collection.immutable.List<TypingResult> types) {
        return typesToString(new ArrayList<>(JavaConverters.asJavaCollection(types)));
    }

    @Override
    public String message() {
        String name = functionName();
        String found = typesToString(found());
        String expected = expectedString();
        return "Mismatch parameter types. Found: " + name + "(" + found + "). Required: " + name + "(" + expected + ")";
    }
}


