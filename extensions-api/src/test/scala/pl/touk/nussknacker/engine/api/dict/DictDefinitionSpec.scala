package pl.touk.nussknacker.engine.api.dict

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypedDict, TypedTaggedValue}

class DictDefinitionSpec extends AnyFunSuite with Matchers {

  test("should type dict") {
    val dict = DictInstance(dictId = "id", definition = EmbeddedDictDefinition(Map.empty))

    Typed.fromInstance(dict) shouldBe TypedDict(dictId = "id", valueType = TypedTaggedValue(underlying = Typed(classOf[String]).asInstanceOf[SingleTypingResult], tag = "dictValue:id"))
  }

}
