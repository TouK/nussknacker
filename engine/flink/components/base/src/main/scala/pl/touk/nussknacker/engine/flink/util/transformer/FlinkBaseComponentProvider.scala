package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateWindowsConfig
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.join.{FullOuterJoinTransformer, SingleSideJoinTransformer}
import pl.touk.nussknacker.engine.util.config.DocsConfig

class FlinkBaseComponentProvider extends ComponentProvider {
  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._

    val aggregateWindowsConfig = AggregateWindowsConfig.loadOrDefault(config)

    //When adding/changing stateful components, corresponding changes should be done in LiteBaseComponentProvider!
    val statelessComponents = List(
      ComponentDefinition("for-each", ForEachTransformer).withRelativeDocs("BasicNodes#foreach"),
      ComponentDefinition("union", UnionTransformer).withRelativeDocs("BasicNodes#union"),
      ComponentDefinition("dead-end", SinkFactory.noParam(EmptySink)).withRelativeDocs("DataSourcesAndSinks#deadend"),
      ComponentDefinition("periodic", PeriodicSourceFactory).withRelativeDocs("DataSourcesAndSinks#periodic")
    )

    val statefulComponents = List(
      ComponentDefinition("union-memo", UnionWithMemoTransformer).withRelativeDocs("DataSourcesAndSinks#unionmemo"),
      ComponentDefinition("previousValue", PreviousValueTransformer).withRelativeDocs("DataSourcesAndSinks#previousvalue"),
      ComponentDefinition("aggregate-sliding", SlidingAggregateTransformerV2).withRelativeDocs("AggregatesInTimeWindows#sliding-window"),
      ComponentDefinition("aggregate-tumbling", new TumblingAggregateTransformer(aggregateWindowsConfig)).withRelativeDocs("AggregatesInTimeWindows#tumbling-window"),
      ComponentDefinition("aggregate-session", SessionWindowAggregateTransformer).withRelativeDocs("AggregatesInTimeWindows#session-window"),
      ComponentDefinition("single-side-join", SingleSideJoinTransformer).withRelativeDocs("AggregatesInTimeWindows#single-side-join"),
      ComponentDefinition("full-outer-join", FullOuterJoinTransformer).withRelativeDocs("AggregatesInTimeWindows#single-side-join"),
      ComponentDefinition("delay", DelayTransformer).withRelativeDocs("DataSourcesAndSinks#delay"),
    )

    statefulComponents ++ statelessComponents
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
