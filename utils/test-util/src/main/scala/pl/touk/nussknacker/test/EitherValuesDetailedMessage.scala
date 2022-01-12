package pl.touk.nussknacker.test

import org.scalactic.source
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

trait EitherValuesDetailedMessage {

  import scala.language.implicitConversions

  implicit def convertEitherToValuable[L, R](either: Either[L, R])(implicit pos: source.Position): EitherValuable[L, R] = new EitherValuable(either, pos)

  class EitherValuable[L, R](either: Either[L, R], pos: source.Position) {
    def rightValue: R = {
      either match {
        case Right(value) => value
        case Left(value) =>
          throw new TestFailedException((_: StackDepthException) => Some(s"The Either on which rightValue was invoked was defined as Left($value)"), None, pos)
      }
    }
  }

}
