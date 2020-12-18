package pl.touk.nussknacker.engine.management.periodic

trait Clock {
  def currentTimestamp: Long
}

object SystemClock extends Clock {
  override def currentTimestamp: Long = System.currentTimeMillis()
}
