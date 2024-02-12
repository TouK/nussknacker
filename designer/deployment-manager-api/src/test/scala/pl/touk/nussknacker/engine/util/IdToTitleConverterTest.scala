package pl.touk.nussknacker.engine.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class IdToTitleConverterTest extends AnyFunSuite with Matchers {

  test("convert kebab-case to Fully Capitalized Title") {
    IdToTitleConverter.toTitle("") shouldEqual ""
    IdToTitleConverter.toTitle("foo") shouldEqual "Foo"
    IdToTitleConverter.toTitle("foo-bar") shouldEqual "Foo Bar"
  }

}
