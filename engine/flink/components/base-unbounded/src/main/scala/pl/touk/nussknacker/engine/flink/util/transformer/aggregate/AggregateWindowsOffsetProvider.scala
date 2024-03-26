package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import scala.concurrent.duration.{Duration, MILLISECONDS}

object AggregateWindowsOffsetProvider {

  def offset(windowDuration: Duration, expectedOffset: Duration): Duration = {
    Duration.apply(expectedOffset.toMillis % windowDuration.toMillis, MILLISECONDS)
  }

}
