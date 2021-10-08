package pl.touk.nussknacker.test

import org.scalactic.source
import org.scalatest.{Assertion, Assertions}

import scala.reflect.ClassTag

trait NussknackerAssertions extends Assertions {
  def assertThrowsWithParent[T <: AnyRef](f: => Any)(implicit classTag: ClassTag[T], pos: source.Position): Assertion = {
    assertThrows[T] {
      try {
        f
      } catch {
        case u: Throwable if u.getCause != null =>
          throw u.getCause
      }
    }
  }
}
