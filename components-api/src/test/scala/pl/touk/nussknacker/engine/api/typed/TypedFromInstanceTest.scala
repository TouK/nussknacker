package pl.touk.nussknacker.engine.api.typed

import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.api.typed.typing.{Unknown, _}

class TypedFromInstanceTest extends AnyFunSuite with Matchers with LoneElement with TableDrivenPropertyChecks {

  import scala.jdk.CollectionConverters._

  test("should type null") {
    Typed.fromInstance(null: Any) shouldBe TypedNull
  }

  test("should type string") {
    Typed.fromInstance("t") shouldBe TypedObjectWithValue(Typed.typedClass[String], "t")
  }

  test("should type int") {
    Typed.fromInstance(1547) shouldBe TypedObjectWithValue(Typed.typedClass[Int], 1547)
  }

  test("should type long") {
    Typed.fromInstance(42L) shouldBe TypedObjectWithValue(Typed.typedClass[Long], 42L)
  }

  test("should type float") {
    Typed.fromInstance(1.4f) shouldBe TypedObjectWithValue(Typed.typedClass[Float], 1.4f)
  }

  test("should type double") {
    Typed.fromInstance(15.78d) shouldBe TypedObjectWithValue(Typed.typedClass[Double], 15.78d)
  }

  test("should type bool") {
    Typed.fromInstance(true) shouldBe TypedObjectWithValue(Typed.typedClass[Boolean], true)
  }

  test("should type map types") {
    val fieldTypes = Map(
      "a" -> TypedObjectWithValue(Typed.typedClass[Int], 1),
      "b" -> TypedObjectWithValue(Typed.typedClass[String], "string")
    )

    val data: List[(Object, TypedObjectTypingResult)] = List(
      (
        Map("a" -> 1, "b" -> "string"),
        Typed.record(fieldTypes, Typed.genericTypeClass(classOf[Map[_, _]], List(Typed[String], Unknown)))
      ),
      (Map("a" -> 1, "b" -> "string").asJava, Typed.record(fieldTypes)),
      (TypedMap(Map("a" -> 1, "b" -> "string")), Typed.record(fieldTypes))
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
      typingResult.withoutValue
        .asInstanceOf[TypedClass]
        .params
        .loneElement
        .canBeSubclassOf(paramTypingResult) shouldBe true
    }

    def checkNotASubclassOfOtherParamTypingResult(obj: Any, otherParamTypingResult: TypingResult): Unit = {
      val typingResult = Typed.fromInstance(obj)
      typingResult.withoutValue
        .asInstanceOf[TypedClass]
        .params
        .loneElement
        .canBeSubclassOf(otherParamTypingResult) shouldBe false
    }

    val listOfSimpleObjects = List[Any](1.1, 2)
    checkTypingResult(listOfSimpleObjects, classOf[List[_]], Typed(classOf[Number]))
    checkTypingResult(listOfSimpleObjects.asJava, classOf[java.util.List[_]], Typed(classOf[Number]))

    val listOfTypedMaps      = List(TypedMap(Map("a" -> 1, "b" -> "B")), TypedMap(Map("a" -> 1)))
    val typedMapTypingResult = Typed.record(Map("a" -> Typed(classOf[Integer])))
    checkTypingResult(listOfTypedMaps, classOf[List[_]], typedMapTypingResult)
    checkTypingResult(listOfTypedMaps.asJava, classOf[java.util.List[_]], typedMapTypingResult)
    checkNotASubclassOfOtherParamTypingResult(
      listOfTypedMaps,
      Typed.record(Map("c" -> Typed(classOf[Integer])))
    )
    checkNotASubclassOfOtherParamTypingResult(
      listOfTypedMaps,
      Typed.record(Map("b" -> Typed(classOf[Integer])))
    )
    checkNotASubclassOfOtherParamTypingResult(
      listOfTypedMaps,
      Typed.record(Map("a" -> Typed(classOf[String])))
    )
  }

  test("should find element type for scala lists of different elements") {
    Typed.fromInstance(List[Any](4L, 6.35, 8.47)) shouldBe Typed.genericTypeClass(
      classOf[List[_]],
      List(Typed.typedClass[Number])
    )
    Typed.fromInstance(List(3, "t")) shouldBe Typed.genericTypeClass(classOf[List[_]], List(Unknown))
  }

  test("should find element type and keep values for java lists of different elements") {
    val numberList = List(4L, 6.35, 8.47).asJava
    Typed.fromInstance(numberList) shouldBe typedListWithElementValues(
      Typed.typedClass[Double],
      numberList
    )

    val anyList = List(3, "t").asJava
    Typed.fromInstance(anyList) shouldBe typedListWithElementValues(
      Unknown,
      anyList
    )
  }

  test("should fallback to object's class") {
    Typed.fromInstance("abc") shouldBe TypedObjectWithValue(Typed.typedClass[String], "abc")
  }

  test("should not save types that cannot be encoded") {
    Typed.fromInstance(Float.NaN) shouldBe Typed.typedClass[Float]
    Typed.fromInstance(Double.PositiveInfinity) shouldBe Typed.typedClass[Double]
    Typed.fromInstance(TestClass(8)) shouldBe Typed.typedClass[TestClass]
  }

  case class TestClass(value: Int)
}
