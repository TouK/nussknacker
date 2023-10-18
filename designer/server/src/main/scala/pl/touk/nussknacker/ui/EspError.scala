package pl.touk.nussknacker.ui

object EspError {

  type XError[A] = Either[EspError, A]

}

abstract class EspError(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class FatalError(message: String, cause: Throwable) extends EspError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class NotFoundError(message: String, cause: Throwable) extends EspError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class BadRequestError(message: String, cause: Throwable) extends EspError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class IllegalOperationError(message: String, cause: Throwable) extends EspError(message, cause) {
  def this(message: String) = this(message, null)
}
