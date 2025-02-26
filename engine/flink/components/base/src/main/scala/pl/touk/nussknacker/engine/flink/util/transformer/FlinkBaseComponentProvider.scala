package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentProvider,
  DesignerWideComponentId,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.common.components.DecisionTable
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.util.config.DocsConfig

class FlinkBaseComponentProvider extends ComponentProvider {
  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig = DocsConfig(config)
    FlinkBaseComponentProvider.create(docsConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

object FlinkBaseComponentProvider {

  val Components: List[ComponentDefinition] =
    create(DocsConfig.Default)

  def create(docsConfig: DocsConfig): List[ComponentDefinition] = {
    import docsConfig._

    List(
      ComponentDefinition("for-each", ForEachTransformer)
        .withRelativeDocs("BasicNodes#foreach")
        .withDesignerWideId("for-each"),
      ComponentDefinition("union", UnionTransformer)
        .withRelativeDocs("BasicNodes#union")
        .withDesignerWideId("union"),
      ComponentDefinition("dead-end", SinkFactory.noParam(EmptySink))
        .withRelativeDocs("DataSourcesAndSinks#deadend")
        .withDesignerWideId("dead-end"),
      ComponentDefinition(name = "decision-table", component = DecisionTable)
        .withRelativeDocs("Enrichers/#decision-table")
        .withDesignerWideId("decision-table"),
      ComponentDefinition("delay", DelayTransformer)
        .withRelativeDocs("DataSourcesAndSinks#delay"),
      ComponentDefinition("previousValue", PreviousValueTransformer)
        .withRelativeDocs("DataSourcesAndSinks#previousvalue"),
    )
  }

}
