package pl.touk.nussknacker.engine.api.typed

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, LoneElement, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing._

import scala.collection.immutable.ListMap

class TypedFromInstanceTest extends FunSuite with Matchers with LoneElement with TableDrivenPropertyChecks {

  import scala.collection.JavaConverters._

  test("should type null") {
    Typed.fromInstance(null: Any) shouldBe Typed.empty
  }

  test("should type map types") {
    val fieldTypes = ListMap("a" -> Typed(classOf[java.lang.Integer]), "b" -> Typed(classOf[java.lang.String]))

    val data: List[(Object, TypedObjectTypingResult)] = List(
      (Map("a" -> 1, "b" -> "string"), TypedObjectTypingResult(fieldTypes, Typed.typedClass(classOf[Map[_, _]], List(Typed[String], Unknown)))),
      (Map("a" -> 1, "b" -> "string").asJava, TypedObjectTypingResult(fieldTypes)),
      (TypedMap(Map("a" -> 1, "b" -> "string")), TypedObjectTypingResult(fieldTypes))
    )

    forEvery(Table(("map", "excepted"), data: _*)) { (map, excepted) =>
      Typed.fromInstance(map) shouldBe excepted
    }
  }

  test("should type empty list") {
    Typed.fromInstance(Nil).canBeSubclassOf(Typed(classOf[List[_]])) shouldBe true
    Typed.fromInstance(Nil.asJava).canBeSubclassOf(Typed(classOf[java.util.List[_]])) shouldBe true
  }

  test("should type lists and return union of types coming from all elements") {
    def checkTypingResult(obj: Any, klass: Class[_], paramTypingResult: TypingResult): Unit = {
      val typingResult = Typed.fromInstance(obj)

      typingResult.canBeSubclassOf(Typed(klass)) shouldBe true
      typingResult.asInstanceOf[TypedClass].params.loneElement.canBeSubclassOf(paramTypingResult) shouldBe true
    }

    def checkNotASubclassOfOtherParamTypingResult(obj: Any, otherParamTypingResult: TypingResult): Unit = {
      val typingResult = Typed.fromInstance(obj)
      typingResult.asInstanceOf[TypedClass].params.loneElement.canBeSubclassOf(otherParamTypingResult) shouldBe false
    }

    val listOfSimpleObjects = List[Any](1.1, 2)
    checkTypingResult(listOfSimpleObjects, classOf[List[_]], Typed(classOf[Integer]))
    checkTypingResult(listOfSimpleObjects.asJava, classOf[java.util.List[_]], Typed(classOf[Integer]))

    val listOfTypedMaps = List(TypedMap(Map("a" -> 1, "b" -> "B")), TypedMap(Map("a" -> 1)))
    val typedMapTypingResult = TypedObjectTypingResult(ListMap("a" -> Typed(classOf[Integer])))
    checkTypingResult(listOfTypedMaps, classOf[List[_]], typedMapTypingResult)
    checkTypingResult(listOfTypedMaps.asJava, classOf[java.util.List[_]], typedMapTypingResult)
    checkNotASubclassOfOtherParamTypingResult(listOfTypedMaps, TypedObjectTypingResult(ListMap("c" -> Typed(classOf[Integer]))))
    checkNotASubclassOfOtherParamTypingResult(listOfTypedMaps, TypedObjectTypingResult(ListMap("b" -> Typed(classOf[Integer]))))
    checkNotASubclassOfOtherParamTypingResult(listOfTypedMaps, TypedObjectTypingResult(ListMap("a" -> Typed(classOf[String]))))
  }

  test("should fallback to object's class") {
    Typed.fromInstance("abc") shouldBe Typed(classOf[java.lang.String])
  }
}
