package pl.touk.esp.ui

import cats.data.Xor

object EspError {

  type XError[A] = Xor[EspError, A]

}

trait EspError {

  def getMessage: String

}

trait FatalError extends EspError

trait NotFoundError extends EspError

trait BadRequestError extends EspError