package pl.touk.nussknacker.test.retry

import org.scalatest.concurrent.AbstractPatienceConfiguration
import org.scalatest.time.{Millis, Span}

import scala.concurrent.duration.FiniteDuration

object PatienceConfigToRetryPolicyConverter extends PatienceConfigToRetryPolicyConverter(Span(100, Millis))

class PatienceConfigToRetryPolicyConverter(private[test] val delta: Span) {

  def toRetryPolicy(patience: AbstractPatienceConfiguration#PatienceConfig): retry.Policy = {
    val (maxAttempts, interval) = toRetryPolicyInternal(patience)
    retry.Pause(maxAttempts, interval)
  }

  private[test] def toRetryPolicyInternal(patience: AbstractPatienceConfiguration#PatienceConfig): (Int, FiniteDuration) = {
    val totalTime   = patience.timeout - delta
    val maxAttempts = Math.max(Math.round(totalTime / patience.interval).toInt, 1)
    val interval    = totalTime / maxAttempts
    (maxAttempts, interval)
  }

}
