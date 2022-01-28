package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.join.SingleSideJoinTransformer
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.engine.util.config.DocsConfig.ComponentConfig

class FlinkBaseComponentProvider extends ComponentProvider {
  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    implicit val docsConfig: DocsConfig = new DocsConfig(config)
    statefulComponents ++ statelessComponents
  }

  //When adding/changing stateful components, corresponding changes should be done in LiteBaseComponentProvider!
  private def statelessComponents(implicit docs: DocsConfig): List[ComponentDefinition] = List(
    ComponentDefinition("for-each", ForEachTransformer).withRelativeDocs("BasicNodes#foreach"),
    ComponentDefinition("union", UnionTransformer).withRelativeDocs("BasicNodes#union"),
    ComponentDefinition("dead-end", SinkFactory.noParam(EmptySink)).withRelativeDocs("BasicNodes#deadend"),
    ComponentDefinition("periodic", PeriodicSourceFactory)
  )

  private def statefulComponents(implicit docs: DocsConfig): List[ComponentDefinition] = List(
    ComponentDefinition("union-memo", UnionWithMemoTransformer).withRelativeDocs("BasicNodes#unionmemo"),
    ComponentDefinition("previousValue", PreviousValueTransformer).withRelativeDocs("BasicNodes#previousvalue"),
    ComponentDefinition("aggregate-sliding", SlidingAggregateTransformerV2).withRelativeDocs("AggregatesInTimeWindows#sliding-window"),
    ComponentDefinition("aggregate-tumbling", TumblingAggregateTransformer).withRelativeDocs("AggregatesInTimeWindows#tumbling-window"),
    ComponentDefinition("aggregate-session", SessionWindowAggregateTransformer).withRelativeDocs("AggregatesInTimeWindows#session-window"),
    ComponentDefinition("single-side-join", SingleSideJoinTransformer).withRelativeDocs("AggregatesInTimeWindows#single-side-join"),
    ComponentDefinition("delay", DelayTransformer).withRelativeDocs("BasicNodes#delay"),
  )

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
