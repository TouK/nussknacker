package pl.touk.nussknacker.ui

import akka.http.scaladsl.model.{StatusCode, StatusCodes}


object Error {

  type XError[A] = Either[Error, A]

}

trait Error {
  def getMessage: String
  val statusCode: Option[StatusCode] = None
}

trait FatalError extends Error {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.InternalServerError)
}

trait NotFoundError extends Error {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.NotFound)
}

trait BadRequestError extends Error {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.BadRequest)
}

trait IllegalOperationError extends Error {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.Conflict)
}