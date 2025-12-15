package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

object AggregateWindowsOffsetProvider {

  def offset(windowDuration: FiniteDuration, expectedOffset: FiniteDuration): FiniteDuration = {
    FiniteDuration(expectedOffset.toMillis % windowDuration.toMillis, MILLISECONDS)
  }

}
