package pl.touk.nussknacker.engine.flink

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.util.transformer.{EventGeneratorSourceFactory, UnionWithMemoTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateWindowsConfig
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{
  SessionWindowAggregateTransformer,
  SlidingAggregateTransformerV2,
  TumblingAggregateTransformer
}
import pl.touk.nussknacker.engine.flink.util.transformer.join.{FullOuterJoinTransformer, SingleSideJoinTransformer}
import pl.touk.nussknacker.engine.util.config.DocsConfig

class FlinkBaseUnboundedComponentProvider extends ComponentProvider {
  override def providerName: String = "baseUnbounded"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig             = DocsConfig(config)
    val aggregateWindowsConfig = AggregateWindowsConfig.loadOrDefault(config)
    FlinkBaseUnboundedComponentProvider.create(docsConfig, aggregateWindowsConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

object FlinkBaseUnboundedComponentProvider {

  val Components: List[ComponentDefinition] =
    create(DocsConfig.Default, AggregateWindowsConfig.Default)

  def create(docsConfig: DocsConfig, aggregateWindowsConfig: AggregateWindowsConfig): List[ComponentDefinition] = {
    import docsConfig._

    // When adding/changing stateful components, corresponding changes should be done in LiteBaseComponentProvider!
    val statelessComponents = List(
      ComponentDefinition("event-generator", EventGeneratorSourceFactory).withRelativeDocs(
        "DataSourcesAndSinks#event-generator"
      ),
    )

    val statefulComponents = List(
      ComponentDefinition("union-memo", UnionWithMemoTransformer).withRelativeDocs("DataSourcesAndSinks#unionmemo"),
      ComponentDefinition("aggregate-sliding", SlidingAggregateTransformerV2).withRelativeDocs(
        "AggregatesInTimeWindows#sliding-window"
      ),
      ComponentDefinition("aggregate-tumbling", new TumblingAggregateTransformer(aggregateWindowsConfig))
        .withRelativeDocs("AggregatesInTimeWindows#tumbling-window"),
      ComponentDefinition("aggregate-session", SessionWindowAggregateTransformer).withRelativeDocs(
        "AggregatesInTimeWindows#session-window"
      ),
      ComponentDefinition("single-side-join", SingleSideJoinTransformer).withRelativeDocs(
        "AggregatesInTimeWindows#single-side-join"
      ),
      ComponentDefinition("full-outer-join", FullOuterJoinTransformer).withRelativeDocs(
        "AggregatesInTimeWindows#full-outer-join"
      ),
    )

    statefulComponents ++ statelessComponents
  }

}
