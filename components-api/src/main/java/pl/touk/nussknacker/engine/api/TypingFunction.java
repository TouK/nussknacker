package pl.touk.nussknacker.engine.api;

import cats.data.NonEmptyList;
import cats.data.Validated;
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult;
import scala.collection.immutable.List;

public interface TypingFunction {
    Validated<NonEmptyList<String>, TypingResult> apply(List<TypingResult> arguments);
}
