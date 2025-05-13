package pl.touk.nussknacker.ui.process.periodic

class PeriodicProcessException(message: String, parent: Throwable) extends RuntimeException(message, parent) {
  def this(message: String) = this(message, null)
}
