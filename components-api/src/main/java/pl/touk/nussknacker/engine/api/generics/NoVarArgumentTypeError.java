package pl.touk.nussknacker.engine.api.generics;

import pl.touk.nussknacker.engine.api.typed.typing;
import scala.collection.JavaConverters;

import java.util.ArrayList;
import java.util.List;

public final class NoVarArgumentTypeError extends ArgumentTypeError {
    private final List<typing.TypingResult> expected;
    private final List<typing.TypingResult> found;
    private final String functionName;

    public NoVarArgumentTypeError(List<typing.TypingResult> expected_,
                                  List<typing.TypingResult> found_,
                                  String functionName_) {
        expected = expected_;
        found = found_;
        functionName = functionName_;
    }

    public NoVarArgumentTypeError(scala.collection.immutable.List<typing.TypingResult> expected_,
                                  scala.collection.immutable.List<typing.TypingResult> found_,
                                  String functionName_) {
        expected = new ArrayList<>(JavaConverters.asJavaCollection(expected_));
        found = new ArrayList<>(JavaConverters.asJavaCollection(found_));
        functionName = functionName_;
    }

    @Override
    public List<typing.TypingResult> found() {
        return found;
    }

    @Override
    public String functionName() {
        return functionName;
    }

    @Override
    public String expectedString() {
        return typesToString(expected);
    }
}
