package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.Writer
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.common.components.DecisionTable
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSink
import pl.touk.nussknacker.engine.lite.api.{commonTypes, customComponentTypes}
import pl.touk.nussknacker.engine.util.config.DocsConfig

import scala.language.higherKinds

class LiteBaseComponentProvider extends ComponentProvider {

  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig: DocsConfig = DocsConfig(config)
    LiteBaseComponentProvider.create(docsConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

object LiteBaseComponentProvider {

  val Components: List[ComponentDefinition] = create(DocsConfig.Default)

  def create(docsConfig: DocsConfig): List[ComponentDefinition] = {
    import docsConfig._
    List(
      ComponentDefinition("for-each", ForEachTransformer)
        .withRelativeDocs("BasicNodes#foreach")
        .withDesignerWideId("for-each"),
      ComponentDefinition("union", Union)
        .withRelativeDocs("BasicNodes#union")
        .withDesignerWideId("union"),
      ComponentDefinition("dead-end", SinkFactory.noParam(DeadEndSink))
        .withRelativeDocs("DataSourcesAndSinks#deadend")
        .withDesignerWideId("dead-end"),
      ComponentDefinition(name = "decision-table", component = DecisionTable)
        .withRelativeDocs("Enrichers/#decision-table")
        .withDesignerWideId("decision-table")
    )

  }

}

object DeadEndSink extends LiteSink[Nothing] {

  override def createTransformation[F[_]: Monad](
      evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]
  ): (typing.TypingResult, commonTypes.DataBatch => F[ResultType[(Context, Nothing)]]) =
    (typing.Unknown, _ => implicitly[Monad[F]].pure(Writer.value(List.empty)))

}
