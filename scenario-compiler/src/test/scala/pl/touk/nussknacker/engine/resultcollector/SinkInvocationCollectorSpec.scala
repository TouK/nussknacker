package pl.touk.nussknacker.engine.resultcollector

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.SinkTestValueDisplayable

class SinkInvocationCollectorSpec extends AnyFunSuite with Matchers {

  test("should normalize displayable sink value") {
    SinkInvocationCollector.normalizeCollectedResult(TestDisplayableValue("a", 1)) shouldBe
      Map("value" -> Map("a" -> 1))
  }

  test("should keep non-displayable value unchanged") {
    val nonDisplayable = NonDisplayableValue("x")
    SinkInvocationCollector.normalizeCollectedResult(nonDisplayable) shouldBe nonDisplayable
  }

  private case class TestDisplayableValue(field: String, v: Int) extends SinkTestValueDisplayable {
    override def asDisplayableValue: Any = Map("value" -> Map(field -> v))
  }

  private case class NonDisplayableValue(v: String)

}
