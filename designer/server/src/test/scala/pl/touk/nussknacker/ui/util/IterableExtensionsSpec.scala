package pl.touk.nussknacker.ui.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.util.IterableExtensions.Chunked

class IterableExtensionsSpec extends AnyFunSuite with Matchers {

  test("should group by bytes size") {
    List("ab", "cde", "fgh").groupByMaxChunkSize(5) shouldBe List(List("ab", "cde"), List("fgh"))
  }

}
