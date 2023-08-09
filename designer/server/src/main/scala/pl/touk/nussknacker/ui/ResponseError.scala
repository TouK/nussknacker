package pl.touk.nussknacker.ui

import akka.http.scaladsl.model.{StatusCode, StatusCodes}


object ResponseError {

  type XError[A] = Either[ResponseError, A]

}

trait ResponseError extends Exception {
  val message: String
  val statusCode: Option[StatusCode] = None
}

trait FatalError extends ResponseError {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.InternalServerError)
}

trait NotFoundError extends ResponseError {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.NotFound)
}

trait BadRequestError extends ResponseError {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.BadRequest)
}

trait IllegalOperationError extends ResponseError {
  override val statusCode: Option[StatusCode] = Some(StatusCodes.Conflict)
}