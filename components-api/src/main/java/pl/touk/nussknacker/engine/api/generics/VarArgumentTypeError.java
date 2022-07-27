package pl.touk.nussknacker.engine.api.generics;

import pl.touk.nussknacker.engine.api.typed.typing;
import scala.collection.JavaConverters;

import java.util.ArrayList;
import java.util.List;

public final class VarArgumentTypeError extends ArgumentTypeError {
    private final List<typing.TypingResult> expected;
    private final typing.TypingResult expectedVarArg;
    private final List<typing.TypingResult> found;
    private final String functionName;

    public VarArgumentTypeError(List<typing.TypingResult> expected_,
                                typing.TypingResult expectedVarArg_,
                                List<typing.TypingResult> found_,
                                String functionName_) {
        expected = expected_;
        expectedVarArg = expectedVarArg_;
        found = found_;
        functionName = functionName_;
    }

    public VarArgumentTypeError(scala.collection.immutable.List<typing.TypingResult> expected_,
                                typing.TypingResult expectedVarArg_,
                                scala.collection.immutable.List<typing.TypingResult> found_,
                                String functionName_) {
        expected = new ArrayList<>(JavaConverters.asJavaCollection(expected_));
        expectedVarArg = expectedVarArg_;
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
        List<typing.TypingResult> temp = new ArrayList<>(expected);
        temp.add(expectedVarArg);
        return typesToString(temp) + "...";
    }
}
