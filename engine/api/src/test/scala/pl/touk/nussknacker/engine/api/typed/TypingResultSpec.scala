package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

class TypingResultSpec extends FunSuite with Matchers {

  private def typeMap(args: (String, TypingResult)*) = TypedObjectTypingResult(args.toMap)

  private def list(arg: TypingResult) =TypedClass(classOf[java.util.List[_]], List(arg))

  test("determine if can be subclass for typed object") {

    typeMap("field1" -> Typed[String], "field2" -> Typed[Int]).canBeSubclassOf(
      typeMap("field1" -> Typed[String])
    ) shouldBe true

    typeMap("field1" -> Typed[String]).canBeSubclassOf(
          typeMap("field1" -> Typed[String], "field2" -> Typed[Int])
    ) shouldBe false

    typeMap("field1" -> Typed[Int]).canBeSubclassOf(
      typeMap("field1" -> Typed[String])
    ) shouldBe false

    typeMap("field1" -> Typed[Int]).canBeSubclassOf(
      typeMap("field1" -> Typed[Number])
    ) shouldBe true

    typeMap("field1" -> list(typeMap("field2" -> Typed[String], "field3" -> Typed[Int]))).canBeSubclassOf(
      typeMap("field1" -> list(typeMap("field2" -> Typed[String])))
    ) shouldBe true

    typeMap("field1" -> list(typeMap("field2a" -> Typed[String], "field3" -> Typed[Int]))).canBeSubclassOf(
      typeMap("field1" -> list(typeMap("field2" -> Typed[String])))
    ) shouldBe false

    typeMap("field1" -> Typed[String]).canBeSubclassOf(Typed[java.util.Map[_, _]]) shouldBe true

    Typed[java.util.Map[_, _]].canBeSubclassOf(typeMap("field1" -> Typed[String])) shouldBe false
  }

  test("determine if can be subclass for typed unions") {
    Typed(Typed[String], Typed[Int]).canBeSubclassOf(Typed[Int]) shouldBe true
    Typed[Int].canBeSubclassOf(Typed(Typed[String], Typed[Int])) shouldBe true

    Typed(Typed[String], Typed[Int]).canBeSubclassOf(
      Typed(Typed[Long], Typed[Int])) shouldBe true
  }

  test("determine if can be subclass for unknown") {
    Unknown.canBeSubclassOf(Typed[Int]) shouldBe true
    Typed[Int].canBeSubclassOf(Unknown) shouldBe true

    Unknown.canBeSubclassOf(Typed(Typed[String], Typed[Int])) shouldBe true
    Typed(Typed[String], Typed[Int]).canBeSubclassOf(Unknown) shouldBe true

    Unknown.canBeSubclassOf(typeMap("field1" -> Typed[String])) shouldBe true
    typeMap("field1" -> Typed[String]).canBeSubclassOf(Unknown) shouldBe true
  }

  test("determine if can be subclass for class") {
    Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[BigDecimal]))).canBeSubclassOf(Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[Number])))) shouldBe true
    Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[Number]))).canBeSubclassOf(Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[BigDecimal])))) shouldBe true

    Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[BigDecimal]))).canBeSubclassOf(Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[String])))) shouldBe false
    Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[String]))).canBeSubclassOf(Typed(ClazzRef(classOf[Set[_]], List(ClazzRef[BigDecimal])))) shouldBe false
  }

}