package pl.touk.nussknacker.engine.baseengine.api

import cats.Monad
import cats.data.{Writer, WriterT}
import cats.implicits._
import cats.kernel.Monoid
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

import scala.language.higherKinds

object commonTypes {

  object DataBatch {
    def apply(ctxs: Context*): DataBatch = DataBatch(ctxs.toList)
  }

  case class DataBatch(value: List[Context]) {
    def map[T](function: Context => T): List[T] = value.map(function)
  }

  type ErrorType = EspExceptionInfo[_ <: Throwable]

  //Errors are collected, we don't stop processing after encountering error
  type ResultType[T] = Writer[List[ErrorType], List[T]]

  //TODO: express in terms of WriterT[F, ...]
  implicit def monoid[F[_]:Monad, T]: Monoid[F[ResultType[T]]] = {
    val monad = implicitly[Monad[F]]
    Monoid.instance[F[ResultType[T]]](monad.pure(Writer(Nil, Nil)), (x, y) => for {
      xVal <- x
      yVal <- y
    } yield (xVal, yVal) match {
      case (WriterT((errorsX, resultX)), WriterT((errorsY, resultY))) => Writer(errorsX ++ errorsY, resultX ++ resultY)
    })
  }

}
