package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.{RichFunction, RuntimeContext}
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util
import pl.touk.nussknacker.engine.util.KeyedValue
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

trait AggregatorFunctionBase extends RichFunction {

  protected val aggregator: Aggregator

  protected def timeWindowLengthMillis: Long

  def nodeId: NodeId

  protected def aggregateElementType: TypingResult

  protected def convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext

  protected def name: String = "aggregator"

  protected def tags: Map[String, String] = Map(nodeIdTag -> nodeId.id)

  protected lazy val engineRuntimeContext: EngineRuntimeContext = convertToEngineRuntimeContext(getRuntimeContext)

  protected lazy val metricsProvider: MetricsProviderForScenario = engineRuntimeContext.metricsProvider

  protected lazy val timeHistogram: util.metrics.Histogram =
    metricsProvider.histogram(MetricIdentifier(NonEmptyList.of(name, "time"), tags), 10)

  protected lazy val retrievedBucketsHistogram: util.metrics.Histogram =
    metricsProvider.histogram(MetricIdentifier(NonEmptyList.of(name, "retrievedBuckets"), tags), 10)

  protected def minimalResolutionMs: Long = 60000L

  protected def allowedOutOfOrderMs: Long = timeWindowLengthMillis

  protected val outputType: TypingResult = aggregator
    .computeOutputType(aggregateElementType)
    .valueOr(e => throw new IllegalArgumentException("Failed to compute output type: " + e))

  protected def computeTimestampToStore(timestamp: Long): Long =
    (timestamp / minimalResolutionMs) * minimalResolutionMs

}
