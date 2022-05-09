package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.Writer
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSink
import pl.touk.nussknacker.engine.lite.api.{commonTypes, customComponentTypes}
import pl.touk.nussknacker.engine.util.config.DocsConfig

import scala.language.higherKinds

class LiteBaseComponentProvider extends ComponentProvider {

  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._
    List(
      ComponentDefinition("for-each", ForEachTransformer).withRelativeDocs("BasicNodes#foreach"),
      ComponentDefinition("collector", CollectorTransformer),
      ComponentDefinition("union", Union).withRelativeDocs("BasicNodes#union"),
      ComponentDefinition("dead-end", SinkFactory.noParam(DeadEndSink)).withRelativeDocs("BasicNodes#deadend")
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

object DeadEndSink extends LiteSink[Nothing] {
  override def createTransformation[F[_] : Monad](evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]): (typing.TypingResult, commonTypes.DataBatch => F[ResultType[(Context, Nothing)]]) =
    (typing.Unknown, _ => implicitly[Monad[F]].pure(Writer.value(List.empty)))
}
