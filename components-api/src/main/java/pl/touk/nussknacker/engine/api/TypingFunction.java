package pl.touk.nussknacker.engine.api;

import cats.data.NonEmptyList;
import cats.data.Validated;
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult;
import scala.Option;
import scala.Tuple2;
import scala.collection.immutable.List;

public abstract class TypingFunction {
    public Option<List<Tuple2<String, TypingResult>>> staticParameters() {
        return Option.apply(null);
    }

    public Option<TypingResult> staticResult() {
        return Option.apply(null);
    }

    public abstract Validated<NonEmptyList<String>, TypingResult> apply(List<TypingResult> arguments);
}
