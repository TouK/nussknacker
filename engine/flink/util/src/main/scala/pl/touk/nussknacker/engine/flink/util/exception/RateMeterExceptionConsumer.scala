package pl.touk.nussknacker.engine.flink.util.exception

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeterWithCount, MetricUtils}
import pl.touk.nussknacker.engine.util.exception.GenericRateMeterExceptionConsumer
import pl.touk.nussknacker.engine.util.metrics.RateMeter

class RateMeterExceptionConsumer(val underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer with GenericRateMeterExceptionConsumer {

  @transient var metricUtils: MetricUtils = _

  override def open(runtimeContext: RuntimeContext): Unit = {
    underlying.open(runtimeContext)
    metricUtils = new MetricUtils(runtimeContext)
    open()
  }

  override def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter
    = InstantRateMeterWithCount.register(tags, name.toList, metricUtils)

  override def close(): Unit = {
    underlying.close()
  }

}
