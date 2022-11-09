package pl.touk.nussknacker.engine.graph

import io.circe.jawn.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef

class SubprocessRefSerializationSpec extends AnyFunSuite with Matchers {

  test("should deserialize SubprocessRef without outputVariableNames [backwards compatibility test]") {
    decode[SubprocessRef]("""{"id":"1","parameters":[]}""") shouldBe Right(SubprocessRef("1", List()))
    decode[SubprocessRef]("""{"id":"1","parameters":[],"outputVariableNames":null}""") shouldBe Right(SubprocessRef("1", List()))
  }

  test("should deserialize SubprocessRef") {
    decode[SubprocessRef]("""{"id":"1","parameters":[], "outputVariableNames":{"a":"b"}}""") shouldBe Right(SubprocessRef("1", List(), Map("a" -> "b")))
  }
}
