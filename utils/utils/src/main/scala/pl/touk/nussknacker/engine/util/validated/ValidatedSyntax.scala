package pl.touk.nussknacker.engine.util.validated

import cats.Traverse
import cats.data._

import scala.language.{higherKinds, reflectiveCalls}

object ValidatedSyntax {

  // Using travers syntax causes IntelliJ idea errors
  implicit class ValidationTraverseOps2[T[_]: Traverse, Err, B](traverse: T[ValidatedNel[Err, B]]) {
    def sequence: ValidatedNel[Err, T[B]] =
      Traverse[T].sequence[ValidatedNel[Err, *], B](traverse)
  }

}