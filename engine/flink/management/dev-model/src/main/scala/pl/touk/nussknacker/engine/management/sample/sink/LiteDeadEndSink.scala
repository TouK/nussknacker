package pl.touk.nussknacker.engine.management.sample.sink

import cats.Monad
import cats.data.Writer
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.lite.api.{commonTypes, customComponentTypes}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSink

import scala.language.higherKinds

// copy-paste from lite base components to avoid dependency to many utils
object LiteDeadEndSink extends LiteSink[Nothing] {

  override def createTransformation[F[_]: Monad](
      evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]
  ): (typing.TypingResult, commonTypes.DataBatch => F[ResultType[(Context, Nothing)]]) =
    (typing.Unknown, _ => implicitly[Monad[F]].pure(Writer.value(List.empty)))

}
