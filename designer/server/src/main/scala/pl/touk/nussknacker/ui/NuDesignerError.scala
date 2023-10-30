package pl.touk.nussknacker.ui

object NuDesignerError {

  type XError[A] = Either[NuDesignerError, A]

}

abstract class NuDesignerError(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class FatalError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class NotFoundError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class BadRequestError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class IllegalOperationError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}
