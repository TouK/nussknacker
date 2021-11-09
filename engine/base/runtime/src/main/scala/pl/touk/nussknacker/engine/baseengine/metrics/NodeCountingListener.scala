package pl.touk.nussknacker.engine.baseengine.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.util.exception.ExceptionRateMeter
import pl.touk.nussknacker.engine.util.metrics._

class NodeCountingListener extends EmptyProcessListener with WithMetrics {

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    exceptionRateMeter = new ExceptionRateMeter(metricsProvider)
  }

  private val counters = collection.concurrent.TrieMap[String, Counter]()

  private val endRateMeters = collection.concurrent.TrieMap[String, RateMeter]()

  private var exceptionRateMeter: ExceptionRateMeter = _

  private def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter =
    InstantRateMeterWithCount.register(tags, name.toList, metricsProvider)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters
      .getOrElseUpdate(nodeId, metricsProvider.counter(MetricIdentifier(NonEmptyList.of("nodeCount"), Map("nodeId" -> nodeId))))
    counter.update(1)
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit = {
    endRateMeters.getOrElseUpdate(lastNodeId, instantRateMeter(Map("nodeId" -> lastNodeId), NonEmptyList.of("dead_end"))).mark()
  }

  override def sinkInvoked(nodeId: String, ref: String, context: Context, processMetaData: MetaData, param: Any): Unit = {
    endRateMeters.getOrElseUpdate(nodeId, instantRateMeter(Map("nodeId" -> nodeId), NonEmptyList.of("end"))).mark()
  }

  override def exceptionThrown(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
    exceptionRateMeter.markException(exceptionInfo)
  }
}
