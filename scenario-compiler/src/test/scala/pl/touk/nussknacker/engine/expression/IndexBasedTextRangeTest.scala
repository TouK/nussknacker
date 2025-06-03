package pl.touk.nussknacker.engine.expression

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}

class IndexBasedTextRangeTest extends AnyFunSuiteLike with Matchers {

  test("index-based text range to coordinates-based text range conversion works with multi-line string") {
    val text =
      """abc
        |def
        |ghi""".stripMargin

    // Given text is effectively:
    //    | 0C 1C 2C 3C
    // ------------
    // 0R | a  b  c  \n
    // 1R | d  e  f  \n
    // 2R | g  h  i
    //
    // So range 6-7 will cover "f \n" so expected coordinates are <2C-1R ; 3C-1R>

    IndexBasedTextRange(6, 7).toCoordinatesBasedTextRange(text) shouldBe CoordinatesBasedTextRange(
      start = TextCoordinates(column = 2, row = 1),
      end = TextCoordinates(column = 3, row = 1),
    )
  }

}
