package pl.touk.nussknacker.engine.flink.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{JobData, Lifecycle}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.util.metrics.InstantRateMeterWithCount
import pl.touk.nussknacker.engine.util.exception.GenericRateMeterExceptionConsumer
import pl.touk.nussknacker.engine.util.metrics.{MetricsProvider, RateMeter}

class RateMeterExceptionConsumer(val underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer with GenericRateMeterExceptionConsumer with Lifecycle  {

  @transient var metricUtils: MetricsProvider = _

  override def open(jobData: JobData, context: EngineRuntimeContext): Unit = {
    metricUtils = context.metricsProvider
    underlying.open(jobData, context)
    underlying.open(jobData)
  }

  override def close(): Unit = underlying.close()

  override def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter
    = InstantRateMeterWithCount.register(tags, name.toList, metricUtils)

}
