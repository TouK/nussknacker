package pl.touk.nussknacker.test

import org.scalactic.source
import org.scalatest.{Assertion, Assertions}

import scala.reflect.ClassTag

trait NussknackerAssertions extends Assertions {
  type ThrowableWithParent = {
    def parent: Throwable
  }

  def assertThrowsWithParent[T <: AnyRef](f: => Any)(implicit classTag: ClassTag[T], pos: source.Position): Assertion = {
    assertThrows[T] {
      try {
        f
        false
      } catch {
        case u: Throwable with ThrowableWithParent =>
          throw u.parent
        case _ =>
          false
      }
    }
  }
}
