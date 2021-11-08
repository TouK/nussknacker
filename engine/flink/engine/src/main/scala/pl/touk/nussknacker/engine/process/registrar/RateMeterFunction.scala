package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.flink.util.metrics.FlinkMetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.{InstantRateMeterWithCount, RateMeter}

private[registrar] class RateMeterFunction[T](groupId: String, nodeId: String) extends RichMapFunction[T, T] {
  private var instantRateMeter: RateMeter = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    instantRateMeter = InstantRateMeterWithCount.register(Map("nodeId" -> nodeId), List(groupId), new FlinkMetricsProviderForScenario(getRuntimeContext))
  }

  override def map(value: T): T = {
    instantRateMeter.mark()
    value
  }
}