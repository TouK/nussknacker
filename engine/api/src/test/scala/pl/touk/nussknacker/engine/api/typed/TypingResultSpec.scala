package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

class TypingResultSpec extends FunSuite with Matchers with OptionValues {

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
    Typed.fromDetailedType[Set[BigDecimal]].canBeSubclassOf(Typed.fromDetailedType[Set[Number]]) shouldBe true
    Typed.fromDetailedType[Set[Number]].canBeSubclassOf(Typed.fromDetailedType[Set[BigDecimal]]) shouldBe true

    Typed.fromDetailedType[Set[BigDecimal]].canBeSubclassOf(Typed.fromDetailedType[Set[String]]) shouldBe false
    Typed.fromDetailedType[Set[String]].canBeSubclassOf(Typed.fromDetailedType[Set[BigDecimal]]) shouldBe false
  }

  test("find common supertype") {
    Typed.commonSupertype(Typed[String], Typed[String]) shouldEqual Typed[String]
    Typed.commonSupertype(Typed[java.lang.Integer], Typed[java.lang.Double]) shouldEqual Typed[java.lang.Double]
    Typed.commonSupertype(Typed[Int], Typed[Double]) shouldEqual Typed[java.lang.Double]
    Typed.commonSupertype(Typed[Int], Typed[Long]) shouldEqual Typed[java.lang.Long]
    Typed.commonSupertype(Typed[Float], Typed[Long]) shouldEqual Typed[java.lang.Float]
    Typed.commonSupertype(Unknown, Typed[Long]) shouldEqual Typed[Long]
    Typed.commonSupertype(Unknown, Unknown) shouldEqual Unknown
    Typed.commonSupertype(
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[Int], "baz" -> Typed[String])),
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[Long], "baz2" -> Typed[String]))) shouldEqual
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[java.lang.Long]))

    Typed.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    Typed.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    Typed.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  class Animal

  class Pet extends Animal

  class Dog extends Pet

  class Cat extends Pet

  class Plant

  class Cactus extends Plant

}