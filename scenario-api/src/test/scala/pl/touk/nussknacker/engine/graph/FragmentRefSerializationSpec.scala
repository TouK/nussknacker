package pl.touk.nussknacker.engine.graph

import io.circe.jawn.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef

class FragmentRefSerializationSpec extends AnyFunSuite with Matchers {

  test("should deserialize FragmentRef without outputVariableNames [backwards compatibility test]") {
    decode[FragmentRef]("""{"id":"1","parameters":[]}""") shouldBe Right(FragmentRef("1", List()))
    decode[FragmentRef]("""{"id":"1","parameters":[],"outputVariableNames":null}""") shouldBe Right(
      FragmentRef("1", List())
    )
  }

  test("should deserialize FragmentRef") {
    decode[FragmentRef]("""{"id":"1","parameters":[], "outputVariableNames":{"a":"b"}}""") shouldBe Right(
      FragmentRef("1", List(), Map("a" -> "b"))
    )
  }

}
