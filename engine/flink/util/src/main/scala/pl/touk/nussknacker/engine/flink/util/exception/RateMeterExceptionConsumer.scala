package pl.touk.nussknacker.engine.flink.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{JobData, Lifecycle}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.util.metrics.WithMetrics
import pl.touk.nussknacker.engine.util.exception.GenericRateMeterExceptionConsumer
import pl.touk.nussknacker.engine.util.metrics.{InstantRateMeterWithCount, RateMeter}

class RateMeterExceptionConsumer(val underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer with WithMetrics with GenericRateMeterExceptionConsumer with Lifecycle  {

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    underlying.open(context)
  }

  override def close(): Unit = {
    super.close()
    underlying.close()
  }

  override def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter
    = InstantRateMeterWithCount.register(tags, name.toList, metricsProvider)

}
