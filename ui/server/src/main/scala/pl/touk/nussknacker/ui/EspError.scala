package pl.touk.nussknacker.ui


object EspError {

  type XError[A] = Either[EspError, A]

}

trait EspError {

  def getMessage: String

}

trait FatalError extends EspError

trait NotFoundError extends EspError

trait BadRequestError extends EspError

trait IllegalOperationError extends EspError