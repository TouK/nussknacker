package pl.touk.nussknacker.ui.util

import scala.concurrent.{ExecutionContext, Future}

object CatsSyntax {
  import cats._
  import cats.implicits._

  def futureOpt(implicit ec: ExecutionContext) = Functor[Future] compose Functor[Option]

}
