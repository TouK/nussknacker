package pl.touk.nussknacker.engine.util

import org.scalatest.{FunSuite, Matchers}

class UtilSpec extends FunSuite with Matchers {
  import Implicits._

  test("mkCommaSeparatedStringWithPotentialEllipsis") {
    List("a", "b", "c").mkCommaSeparatedStringWithPotentialEllipsis(3) shouldEqual "a, b, c"
    List("a", "b", "c", "d").mkCommaSeparatedStringWithPotentialEllipsis(3) shouldEqual "a, b, c, ..."
  }

}
