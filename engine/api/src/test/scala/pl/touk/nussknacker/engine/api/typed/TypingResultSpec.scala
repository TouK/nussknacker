package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

class TypingResultSpec extends FunSuite with Matchers with OptionValues {

  private val commonSuperTypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Intersection)
  
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

  test("find common supertype for simple types") {
    commonSuperTypeFinder.commonSupertype(Typed[String], Typed[String]) shouldEqual Typed[String]
    commonSuperTypeFinder.commonSupertype(Typed[java.lang.Integer], Typed[java.lang.Double]) shouldEqual Typed[java.lang.Double]
    commonSuperTypeFinder.commonSupertype(Typed[Int], Typed[Double]) shouldEqual Typed[java.lang.Double]
    commonSuperTypeFinder.commonSupertype(Typed[Int], Typed[Long]) shouldEqual Typed[java.lang.Long]
    commonSuperTypeFinder.commonSupertype(Typed[Float], Typed[Long]) shouldEqual Typed[java.lang.Float]
  }

  test("find special types") {
    commonSuperTypeFinder.commonSupertype(Unknown, Unknown) shouldEqual Unknown
    commonSuperTypeFinder.commonSupertype(Unknown, Typed[Long]) shouldEqual Unknown

    commonSuperTypeFinder.commonSupertype(
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[Int], "baz" -> Typed[String])),
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[Long], "baz2" -> Typed[String]))) shouldEqual
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[java.lang.Long], "baz" -> Typed[String], "baz2" -> Typed[String]))

    commonSuperTypeFinder.commonSupertype(
      TypedObjectTypingResult(Map("foo" -> Typed[String])), TypedObjectTypingResult(Map("foo" -> Typed[Long]))) shouldEqual
      TypedObjectTypingResult(Map.empty[String, TypingResult])
  }

  test("find common supertype for complex types with inheritance in classes hierarchy") {
    import ClassHierarchy._
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Pet]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    commonSuperTypeFinder.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  test("find common supertype for complex types with inheritance in interfaces hierarchy") {
    import InterfaceHierarchy._
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Pet]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    commonSuperTypeFinder.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  test("find common supertype for complex types with inheritance in mixins hierarchy") {
    import HierarchyInMixins._
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Pet]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    commonSuperTypeFinder.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  test("common supertype with union of not matching classes strategy") {
    import ClassHierarchy._
    val unionFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Union)
    unionFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed(Typed[Dog], Typed[Cactus])
  }

  object ClassHierarchy {

    class Animal extends Serializable

    class Pet extends Animal

    class Dog extends Pet

    class Cat extends Pet

    class Plant extends Serializable

    class Cactus extends Plant

  }

  object InterfaceHierarchy {

    trait Animal extends Serializable

    trait Pet extends Animal

    trait Dog extends Pet

    trait Cat extends Pet

    trait Plant extends Serializable

    trait Cactus extends Plant

  }

  object HierarchyInMixins {

    trait Animal extends Serializable

    trait Pet extends Animal

    class BaseDog

    class Dog extends BaseDog with Pet

    class BaseCat

    class Cat extends BaseCat with Pet

    trait Plant extends Serializable

    class Cactus extends Plant

  }

}
