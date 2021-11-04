package pl.touk.nussknacker.engine.flink.util.exception

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeterWithCount, MetricUtils}
import pl.touk.nussknacker.engine.util.exception.GenericRateMeterExceptionConsumer
import pl.touk.nussknacker.engine.util.metrics.{MetricsProvider, RateMeter}

class RateMeterExceptionConsumer(val underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer with GenericRateMeterExceptionConsumer {

  @transient var metricUtils: MetricsProvider = _

  override def open(jobData: JobData, context: EngineRuntimeContext): Unit = {
    metricUtils = context.metricsProvider
  }

  override def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter
    = InstantRateMeterWithCount.register(tags, name.toList, metricUtils)

  override def close(): Unit = {
    underlying.close()
  }

}
