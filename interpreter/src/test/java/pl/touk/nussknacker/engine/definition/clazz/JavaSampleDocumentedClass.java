package pl.touk.nussknacker.engine.definition.clazz;

import cats.data.NonEmptyList;
import cats.data.Validated;
import cats.data.Validated.Invalid;
import cats.data.Validated.Valid;
import pl.touk.nussknacker.engine.api.Documentation;
import pl.touk.nussknacker.engine.api.generics.*;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.typed.supertype.*;
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult;
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass;
import scala.collection.immutable.List;

public class JavaSampleDocumentedClass {
    static final String bazDocs = "This is sample documentation for baz method";
    static final String field2Docs = "This is sample documentation for field2 field";
    static final String quxDocs = "This is sample documentation for qux method";
    static final String headDocs = "This is sample documentation for head method";
    static final String maxDocs = "This is sample documentation for max method";

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

    @SafeVarargs
    @Documentation(description = maxDocs)
    @GenericType(typingFunction = MaxHelper.class)
    public final <T extends Number> T max(T... args) {
        T ans = args[0];
        for (T a: args) {
            if (a.doubleValue() > ans.doubleValue()) ans = a;
        }
        return ans;
    }

    static class HeadHelper extends TypingFunction {
        private final Class<?> listClass = java.util.List.class;

        public Validated<NonEmptyList<GenericFunctionTypingError>, TypingResult> computeResultType(List<TypingResult> arguments) {
            if (arguments.length() != 1) {
                return Invalid.invalid(NonEmptyList.one(GenericFunctionTypingError.ArgumentTypeError$.MODULE$));
            }
            if (!(arguments.head() instanceof TypedClass)) {
                return Invalid.invalid(NonEmptyList.one(GenericFunctionTypingError.ArgumentTypeError$.MODULE$));
            }

            TypedClass arg = (TypedClass) arguments.head();
            if (arg.klass() != listClass) {
                return Invalid.invalid(NonEmptyList.one(GenericFunctionTypingError.ArgumentTypeError$.MODULE$));
            }
            if (arg.params().length() != 1) {
                throw new AssertionError("Lists must have one parameter");
            }
            return Valid.valid(arg.params().head());
        }
    }

    static class MaxHelper extends TypingFunction {

        @Override
        public Validated<NonEmptyList<GenericFunctionTypingError>, TypingResult> computeResultType(List<TypingResult> arguments) {
            if (arguments.isEmpty()) {
                return Validated.invalidNel(new GenericFunctionTypingError.OtherError("Max must have at least one argument"));
            }

            TypingResult res = arguments.head();
            for (int i = 1; i != arguments.length(); ++i) {
                res = CommonSupertypeFinder$.MODULE$.Union().commonSupertype(res, arguments.apply(i), NumberTypesPromotionStrategy.ToSupertype$.MODULE$);
            }
            return Validated.validNel(res);
        }
    }
}
