package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.join.SingleSideJoinTransformer

import java.net.URLClassLoader

class FlinkBaseComponentProvider extends ComponentProvider {
  override def providerName: String = "base"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    List(
      ComponentDefinition("union", UnionTransformer),
      ComponentDefinition("union-memo", UnionWithMemoTransformer),
      ComponentDefinition("previousValue", PreviousValueTransformer),
      ComponentDefinition("aggregate-sliding", SlidingAggregateTransformerV2),
      ComponentDefinition("aggregate-tumbling", TumblingAggregateTransformer),
      ComponentDefinition("aggregate-session", SessionWindowAggregateTransformer),
      ComponentDefinition("single-side-join", SingleSideJoinTransformer),
      ComponentDefinition("delay", DelayTransformer),
      ComponentDefinition("dead-end", SinkFactory.noParam(EmptySink)),
      ComponentDefinition("periodic", PeriodicSourceFactory)
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
