package pl.touk.nussknacker.ui.util

import cats.data.Validated

import scala.concurrent.{ExecutionContext, Future}

object CatsSyntax {
  import cats._
  import cats.implicits._

  def futureOpt(implicit ec: ExecutionContext) = Functor[Future] compose Functor[Option]

  def toFuture[E, A](v: Validated[E, A])(invalidToException: E => Exception): Future[A] = {
    v match {
      case Validated.Valid(a) =>
        Future.successful(a)
      case Validated.Invalid(e) =>
        Future.failed(invalidToException(e))
    }
  }

}
