package pl.touk.nussknacker.ui

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.security.api.LoggedUser

object NuDesignerError {

  type XError[A] = Either[NuDesignerError, A]

}

sealed abstract class NuDesignerError(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class NotFoundError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class BadRequestError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}

class UnauthorizedError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(user: LoggedUser) =
    this(s"The supplied user [${user.username}] is not authorized to access this resource", null)
  def this(message: String) = this(message, null)
}

class MissingProcessResolverError(message: String, cause: Throwable) extends FatalError(message, cause) {

  def this(user: LoggedUser, processingType: ProcessingType) =
    this(
      s"Error while providing process resolver for processing type $processingType requested by user ${user.username}",
      null
    )

  def this(message: String) = this(message, null)

}

abstract class IllegalOperationError(message: String, val details: String, cause: Throwable)
    extends NuDesignerError(message, cause) {

  def this(message: String, details: String) = this(message, details, null)
}

abstract class OtherError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}

abstract class FatalError(message: String, cause: Throwable) extends NuDesignerError(message, cause) {
  def this(message: String) = this(message, null)
}
