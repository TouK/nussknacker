package pl.touk.nussknacker.engine.baseengine.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.Lifecycle
import pl.touk.nussknacker.engine.api.exception.EspExceptionConsumer
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.exception.GenericRateMeterExceptionConsumer
import pl.touk.nussknacker.engine.util.metrics.{InstantRateMeterWithCount, MetricsProviderForScenario, RateMeter}

class RateMeterExceptionConsumer extends GenericRateMeterExceptionConsumer with Lifecycle {

  private var metricsProvider: MetricsProviderForScenario = _

  override def underlying: EspExceptionConsumer = _ => {}

  override def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter =
    InstantRateMeterWithCount.register(tags, name.toList, metricsProvider)

  override def open(context: EngineRuntimeContext): Unit = metricsProvider = context.metricsProvider

}
