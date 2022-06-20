package pl.touk.nussknacker.test

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.scalactic.source
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

trait ValidatedValuesDetailedMessage {

  import scala.language.implicitConversions

  implicit def convertValidatedToValuable[E, A](validated: Validated[E, A])(implicit pos: source.Position): ValidatedValuable[E, A] = new ValidatedValuable(validated, pos)

  class ValidatedValuable[E, A](validated: Validated[E, A], pos: source.Position) {
    def validValue: A = {
      validated match {
        case Invalid(value) =>
          throw new TestFailedException((_: StackDepthException) => Some(s"The Validated on which validValue was invoked was defined as Invalid($value)"), None, pos)
        case Valid(value) => value
      }
    }

    def invalidValue: E = {
      validated match {
        case Invalid(value) => value
        case Valid(value) =>
          throw new TestFailedException((_: StackDepthException) => Some(s"The Validated on which invalidValue was invoked was defined as Valid($value)"), None, pos)
      }
    }
  }

}
