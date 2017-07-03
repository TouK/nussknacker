package pl.touk.esp.engine.flink.util.exception

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.MetricGroup
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.esp.engine.flink.util.metrics.InstantRateMeter
import pl.touk.esp.engine.util.exception.GenericRateMeterExceptionConsumer

class RateMeterExceptionConsumer(val underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer with GenericRateMeterExceptionConsumer {

  @transient var errorMetricGroup: MetricGroup = _

  override def open(runtimeContext: RuntimeContext) = {

    underlying.open(runtimeContext)
    errorMetricGroup = runtimeContext.getMetricGroup
    open()
  }

  override def instantRateMeter(name: String*) = name.toList match {
    case Nil => throw new IllegalArgumentException("Empty metric name")
    case nonEmpty =>
      //all parts but last form group name
      val group = nonEmpty.dropRight(1).foldLeft(errorMetricGroup){_.addGroup(_)}
      //last part is metric name in group
      val metricName = nonEmpty.last
      group.gauge[Double, InstantRateMeter](metricName, new InstantRateMeter)
  }

  override def close() = {
    underlying.close()
  }


}
