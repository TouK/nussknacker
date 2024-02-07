package pl.touk.nussknacker.ui.util

import cats.Monad
import cats.data.EitherT
import cats.implicits.toFunctorOps

import scala.language.higherKinds

object EitherTImplicits {

  implicit class EitherTInstance[F[_]: Monad, ERROR, OUTPUT](value: F[Either[ERROR, OUTPUT]]) {
    def eitherT(): EitherT[F, ERROR, OUTPUT] =
      EitherT.apply(value)

    def eitherTMapE[E](mapE: ERROR => E): EitherT[F, E, OUTPUT] =
      EitherT.apply(value.map(_.left.map(mapE)))
  }

  implicit class EitherTFromOptionInstance[F[_]: Monad, ERROR, OUTPUT](value: F[Option[OUTPUT]]) {
    def toRightEitherT(error: ERROR): EitherT[F, ERROR, OUTPUT] =
      EitherT.fromOptionF(value, error)
  }

  implicit class EitherTFromMonad[F[_]: Monad, OUTPUT](value: F[OUTPUT]) {
    def eitherT[ERROR](): EitherT[F, ERROR, OUTPUT] =
      EitherT.liftF(value)
  }

}
