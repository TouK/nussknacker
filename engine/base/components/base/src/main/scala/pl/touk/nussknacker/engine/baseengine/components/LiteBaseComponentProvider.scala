package pl.touk.nussknacker.engine.baseengine.components

import cats.Monad
import cats.data.Writer
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.LiteSink
import pl.touk.nussknacker.engine.baseengine.api.{commonTypes, customComponentTypes}

import scala.language.higherKinds

class LiteBaseComponentProvider extends ComponentProvider {

  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = List(
    ComponentDefinition("split", ProcessSplitter),
    ComponentDefinition("union", Union),
    ComponentDefinition("dead-end", SinkFactory.noParam(DeadEndSink))
  )

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

object DeadEndSink extends LiteSink[Nothing] {
  override def createTransformation[F[_] : Monad](evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]): (typing.TypingResult, commonTypes.DataBatch => F[ResultType[(Context, Nothing)]]) =
    (typing.Unknown, _ => implicitly[Monad[F]].pure(Writer.value(List.empty)))
}