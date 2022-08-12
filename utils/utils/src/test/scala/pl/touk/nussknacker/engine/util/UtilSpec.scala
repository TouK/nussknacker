package pl.touk.nussknacker.engine.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UtilSpec extends AnyFunSuite with Matchers {
  import Implicits._

  test("mkCommaSeparatedStringWithPotentialEllipsis") {
    List("a", "b", "c").mkCommaSeparatedStringWithPotentialEllipsis(3) shouldEqual "a, b, c"
    List("a", "b", "c", "d").mkCommaSeparatedStringWithPotentialEllipsis(3) shouldEqual "a, b, c, ..."
  }

}
