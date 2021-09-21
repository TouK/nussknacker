package pl.touk.nussknacker.engine.kafka.generic

trait DelayCalculator extends Serializable {
  def calculateDelay(currentTime: Long, eventTime: Long): Long
}

class FixedDelayCalculator(delayInMillis: Long) extends DelayCalculator {
  override def calculateDelay(currentTime: Long, eventTime: Long): Long = {
    val eventLatency = currentTime - eventTime
    delayInMillis - eventLatency
  }
}
