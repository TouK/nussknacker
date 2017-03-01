package pl.touk.esp.engine.flink.util.exception

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.MetricGroup
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.esp.engine.flink.util.metrics.InstantRateMeter

class RateMeterExceptionConsumer(underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer {

  private lazy val instantRateMeter = new InstantRateMeter

  private var meterMap = Map[String, InstantRateMeter]()

  @transient var errorMetricGroup: MetricGroup = _

  override def open(runtimeContext: RuntimeContext) = {

    underlying.open(runtimeContext)
    errorMetricGroup = runtimeContext.getMetricGroup.addGroup("error")
    //TODO: usunac po upgrade wszystkich procesow
    errorMetricGroup.gauge[Double, InstantRateMeter]("instantRate", instantRateMeter)
  }

  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]) = {
    try {
      underlying.consume(exceptionInfo)
    } finally {
      instantRateMeter.mark()
      getMeterForNode(exceptionInfo.nodeId.getOrElse("unknown")).mark()
    }
  }

  override def close() = {
    underlying.close()
  }

  private def getMeterForNode(nodeId: String) : InstantRateMeter = meterMap.get(nodeId) match {
    case Some(meter) => meter
    case None =>
      val meter = new InstantRateMeter
      errorMetricGroup.gauge[Double, InstantRateMeter](s"instantRateByNode.$nodeId", meter)
      meterMap = meterMap.updated(nodeId, meter)
      meter
  }

}
