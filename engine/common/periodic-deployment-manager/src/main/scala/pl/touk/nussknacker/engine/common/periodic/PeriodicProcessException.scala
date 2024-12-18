package pl.touk.nussknacker.engine.common.periodic

class PeriodicProcessException(message: String, parent: Throwable) extends RuntimeException(message, parent) {
  def this(message: String) = this(message, null)
}
