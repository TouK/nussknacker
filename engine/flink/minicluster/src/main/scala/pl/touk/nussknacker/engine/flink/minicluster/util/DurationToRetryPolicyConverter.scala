package pl.touk.nussknacker.engine.flink.minicluster.util

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object DurationToRetryPolicyConverter {

  def toPausePolicy(totalTime: FiniteDuration, interval: FiniteDuration): retry.Policy = {
    val (maxAttempts, correctedInterval) = toPausePolicyInternal(totalTime, interval)
    retry.Pause(maxAttempts, correctedInterval)
  }

  // This method is necessary because retry.Pause.apply is a constructor for CountingPolicy and CountingPolicy doesn't expose
  // information such as maxAttempts, interval. Due to this, it is impossible to do verification based on that class in tests.
  private[nussknacker] def toPausePolicyInternal(
      totalTime: FiniteDuration,
      interval: FiniteDuration
  ): (Int, FiniteDuration) = {
    val maxAttempts = {
      // 0 millis case and below is a special case when we can reduce repeats to only one, first attempt
      if (totalTime <= 0.millis) {
        0
      } else {
        // We have to retry at least once because first attempt is done instantly
        Math.max(Math.round(totalTime / interval).toInt, 1)
      }
    }
    val correctedInterval = interval.min(totalTime).max(0.seconds)
    (maxAttempts, correctedInterval)
  }

}

object DurationToRetryPolicyConverterOps extends DurationToRetryPolicyConverterOps(pausePolicyInterval = 1.second)

class DurationToRetryPolicyConverterOps(pausePolicyInterval: FiniteDuration) {

  implicit class DurationOps(totalTime: FiniteDuration) {

    def toPausePolicy: retry.Policy = {
      DurationToRetryPolicyConverter.toPausePolicy(totalTime, pausePolicyInterval)
    }

  }

}
