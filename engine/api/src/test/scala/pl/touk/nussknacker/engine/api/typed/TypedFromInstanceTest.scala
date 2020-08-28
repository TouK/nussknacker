package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.typed.typing._

class TypedFromInstanceTest extends FunSuite with Matchers {

  import scala.collection.JavaConverters._

  test("should type null") {
    Typed.fromInstance(null: Any) shouldBe Typed.empty
  }

  test("should type dict") {
    val dict = DictInstance(dictId = "id", definition = EmbeddedDictDefinition(Map.empty))

    Typed.fromInstance(dict) shouldBe TypedDict(dictId = "id", valueType = TypedTaggedValue(underlying = Typed(classOf[String]).asInstanceOf[SingleTypingResult], tag = "dictValue:id"))
  }

  test("should type typed map") {
    val typedMap = TypedMap(Map("a" -> 1, "b" -> "string"))

    Typed.fromInstance(typedMap) shouldBe TypedObjectTypingResult(Map("a" -> Typed(classOf[java.lang.Integer]), "b" -> Typed(classOf[java.lang.String])))
  }

  test("should type empty list") {
    Typed.fromInstance(Nil).canBeSubclassOf(Typed(classOf[List[_]])) shouldBe true
    Typed.fromInstance(Nil.asJava).canBeSubclassOf(Typed(classOf[java.util.List[_]])) shouldBe true
  }

  test("should type lists based on first element") {
    val listOfSimpleObjects = List(1, 2)
    Typed.fromInstance(listOfSimpleObjects).canBeSubclassOf(TypedClass(classOf[List[_]], List(Typed(classOf[java.lang.Integer])))) shouldBe true
    Typed.fromInstance(listOfSimpleObjects.asJava).canBeSubclassOf(TypedClass(classOf[java.util.List[_]], List(Typed(classOf[java.lang.Integer])))) shouldBe true

    val listOfTypedMaps = List(TypedMap(Map("a" -> 1, "b" -> "B")))
    val typedMapTypingResult = TypedObjectTypingResult(Map("a" -> Typed(classOf[java.lang.Integer]), "b" -> Typed(classOf[java.lang.String])))
    Typed.fromInstance(listOfTypedMaps).canBeSubclassOf(TypedClass(classOf[List[_]], List(typedMapTypingResult))) shouldBe true
    Typed.fromInstance(listOfTypedMaps.asJava).canBeSubclassOf(TypedClass(classOf[java.util.List[_]], List(typedMapTypingResult))) shouldBe true
  }

  test("should fallback to object's class") {
    Typed.fromInstance("abc") shouldBe Typed(classOf[java.lang.String])
  }
}
