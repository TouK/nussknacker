package pl.touk.nussknacker.engine.types;

import cats.data.NonEmptyList;
import cats.data.Validated;
import cats.data.Validated.Invalid;
import cats.data.Validated.Valid;
import pl.touk.nussknacker.engine.api.Documentation;
import pl.touk.nussknacker.engine.api.generics.*;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult;
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass;
import scala.collection.JavaConverters;
import scala.collection.immutable.List;

import java.util.stream.Collectors;

public class JavaSampleDocumentedClass {
    static final String bazDocs = "This is sample documentation for baz method";
    static final String field2Docs = "This is sample documentation for field2 field";
    static final String quxDocs = "This is sample documentation for qux method";
    static final String headDocs = "This is sample documentation for head method";

    public long foo(String fooParam1) {
        return 0L;
    }

    public String bar(@ParamName("barparam1") long barparam1) {
        return "";
    }

    @Documentation(description = bazDocs)
    public long baz(@ParamName("bazparam1") String bazparam1, @ParamName("bazparam2") int bazparam2) {
        return 0L;
    }

    @Documentation(description = quxDocs)
    public long qux(String quxParam1) {
        return 0L;
    }

    public long field1 = 123L;

    @Documentation(description = field2Docs)
    public long field2 = 123L;


    @Documentation(description = headDocs)
    @GenericType(typingFunction = HeadHelper.class)
    public <T> T head(java.util.List<T> list) {
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    static class HeadHelper extends TypingFunction {
        private final Class<?> listClass = java.util.List.class;

        private String argumentsToString(List<TypingResult> arguments) {
            Iterable<String> strings = JavaConverters.asJavaCollection(arguments).stream().map(TypingResult::display).collect(Collectors.toList());
            return String.join(", ", strings);
        }

        private ExpressionParseError error(List<TypingResult> arguments) {
            String expectedString = "head(List[Unknown])";
            String foundString = "head(" + argumentsToString(arguments) + ")";
            return new GenericFunctionError("Mismatch parameter types. Found: " + foundString + ". Required: " + expectedString);
        }

        public Validated<NonEmptyList<ExpressionParseError>, TypingResult> apply(List<TypingResult> arguments) {
            if (arguments.length() != 1) {
                return Invalid.invalid(NonEmptyList.one(error(arguments)));
            }
            if (!(arguments.head() instanceof TypedClass)) {
                return Invalid.invalid(NonEmptyList.one(error(arguments)));
            }

            TypedClass arg = (TypedClass) arguments.head();
            if (arg.klass() != listClass) {
                return Invalid.invalid(NonEmptyList.one(error(arguments)));
            }
            if (arg.params().length() != 1) {
                throw new AssertionError("Lists must have one parameter");
            }
            return Valid.valid(arg.params().head());
        }
    }
}
