package pl.touk.nussknacker.engine.api.typed

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing._

import java.time.{LocalDate, LocalDateTime}
import java.util
import java.util.Currency

class TypingResultSpec extends AnyFunSuite with Matchers with OptionValues with Inside {

  private val commonSuperTypeFinder = CommonSupertypeFinder.Intersection

  private def typeMap(args: (String, TypingResult)*) = TypedObjectTypingResult(args.toMap)

  private def list(arg: TypingResult) = Typed.genericTypeClass[java.util.List[_]](List(arg))

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

  test("extract Unknown value type when no super matching supertype found among all fields of Record") {
    TypedObjectTypingResult(
      Map(
        "foo" -> Typed[String],
        "bar" -> TypedObjectTypingResult(Map.empty[String, TypingResult])
      )
    ).objType.params(1) shouldEqual Unknown

    TypedObjectTypingResult(Map.empty[String, TypingResult]).objType.params(1) shouldEqual Unknown
  }

  test("determine if can be subclass for typed unions") {
    Typed(Typed[String], Typed[Int]).canBeSubclassOf(Typed[Int]) shouldBe true
    Typed[Int].canBeSubclassOf(Typed(Typed[String], Typed[Int])) shouldBe true

    Typed(Typed[String], Typed[Int]).canBeSubclassOf(Typed(Typed[Long], Typed[Int])) shouldBe true
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

  test("determine if numbers can be converted") {
    Typed[Int].canBeSubclassOf(Typed[Long]) shouldBe true
    Typed[Long].canBeSubclassOf(Typed[Int]) shouldBe true
    Typed[Long].canBeSubclassOf(Typed[Double]) shouldBe true
    Typed[Double].canBeSubclassOf(Typed[Long]) shouldBe false
    Typed[java.math.BigDecimal].canBeSubclassOf(Typed[Long]) shouldBe true
    Typed[Long].canBeSubclassOf(Typed[java.math.BigDecimal]) shouldBe true
  }

  test("find common supertype for simple types") {
    implicit val numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMathOperation
    commonSuperTypeFinder.commonSupertype(Typed[String], Typed[String]) shouldEqual Typed[String]
    commonSuperTypeFinder
      .commonSupertype(Typed[java.lang.Integer], Typed[java.lang.Double]) shouldEqual Typed[java.lang.Double]
    commonSuperTypeFinder.commonSupertype(Typed[Int], Typed[Double]) shouldEqual Typed[java.lang.Double]
    commonSuperTypeFinder.commonSupertype(Typed[Int], Typed[Long]) shouldEqual Typed[java.lang.Long]
    commonSuperTypeFinder.commonSupertype(Typed[Float], Typed[Long]) shouldEqual Typed[java.lang.Float]

    commonSuperTypeFinder.commonSupertype(
      Typed[Float],
      Typed.tagged(Typed.typedClass[Float], "example")
    ) shouldEqual Typed(Set.empty)
    commonSuperTypeFinder.commonSupertype(
      Typed.tagged(Typed.typedClass[Float], "example"),
      Typed[Float]
    ) shouldEqual Typed(Set.empty)
  }

  test("find special types") {
    implicit val numberTypesPromotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForMathOperation
    commonSuperTypeFinder.commonSupertype(Unknown, Unknown) shouldEqual Unknown
    commonSuperTypeFinder.commonSupertype(Unknown, Typed[Long]) shouldEqual Unknown

    commonSuperTypeFinder.commonSupertype(
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[Int], "baz" -> Typed[String])),
      TypedObjectTypingResult(Map("foo" -> Typed[String], "bar" -> Typed[Long], "baz2" -> Typed[String]))
    ) shouldEqual
      TypedObjectTypingResult(
        Map("foo" -> Typed[String], "bar" -> Typed[java.lang.Long], "baz" -> Typed[String], "baz2" -> Typed[String])
      )

    commonSuperTypeFinder.commonSupertype(
      TypedObjectTypingResult(Map("foo" -> Typed[String])),
      TypedObjectTypingResult(Map("foo" -> Typed[Long]))
    ) shouldEqual
      TypedObjectTypingResult(Map.empty[String, TypingResult])
  }

  test("find common supertype for complex types with inheritance in classes hierarchy") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    import ClassHierarchy._
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Pet]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    commonSuperTypeFinder.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  test("find common supertype for complex types with inheritance in interfaces hierarchy") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    import InterfaceHierarchy._
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Pet]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    commonSuperTypeFinder.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  test("find common supertype for complex types with inheritance in mixins hierarchy") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    import HierarchyInMixins._
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Pet]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cat]) shouldEqual Typed[Pet]
    commonSuperTypeFinder.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed.empty

    commonSuperTypeFinder.commonSupertype(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat]) shouldEqual Typed[Pet]
  }

  test("common supertype with generics") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    commonSuperTypeFinder.commonSupertype(
      Typed.fromDetailedType[Comparable[Number]],
      Typed.fromDetailedType[Integer]
    ) shouldEqual Typed.fromDetailedType[Comparable[Number]]
    commonSuperTypeFinder.commonSupertype(
      Typed.fromDetailedType[Integer],
      Typed.fromDetailedType[Comparable[Number]]
    ) shouldEqual Typed.fromDetailedType[Comparable[Number]]
    commonSuperTypeFinder.commonSupertype(
      Typed.fromDetailedType[util.List[Integer]],
      Typed.fromDetailedType[util.List[Number]]
    ) shouldEqual Typed.fromDetailedType[util.List[Number]]
    commonSuperTypeFinder.commonSupertype(
      Typed.fromDetailedType[util.List[Integer]],
      Typed.fromDetailedType[util.Collection[Number]]
    ) shouldEqual Typed.fromDetailedType[util.Collection[Number]]
    // below weird examples which will work
    commonSuperTypeFinder.commonSupertype(
      Typed.fromDetailedType[util.List[Number]],
      Typed.fromDetailedType[util.Collection[Integer]]
    ) shouldEqual Typed.fromDetailedType[util.Collection[Number]]
    commonSuperTypeFinder.commonSupertype(
      Typed.fromDetailedType[util.List[String]],
      Typed.fromDetailedType[util.Collection[Integer]]
    ) shouldEqual Typed.genericTypeClass[util.Collection[_]](List(Unknown))
  }

  test("common supertype with union of not matching classes strategy with enabled strictTypeChecking") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    import ClassHierarchy._
    CommonSupertypeFinder.Union.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed(Typed[Dog], Typed[Cactus])
    CommonSupertypeFinder.Union.commonSupertype(
      Typed.tagged(Typed.typedClass[Dog], "dog"),
      Typed[Cactus]
    ) shouldEqual Typed(Set.empty)
    CommonSupertypeFinder.Union.commonSupertype(
      Typed[Cactus],
      Typed.tagged(Typed.typedClass[Dog], "dog")
    ) shouldEqual Typed(Set.empty)
  }

  test("common supertype with union of not matching classes strategy with disabled strictTypeChecking") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    import ClassHierarchy._
    CommonSupertypeFinder.Union.commonSupertype(Typed[Dog], Typed[Cactus]) shouldEqual Typed(Typed[Dog], Typed[Cactus])
  }

  test("determine if can be subclass for tagged value") {
    Typed
      .tagged(Typed.typedClass[String], "tag1")
      .canBeSubclassOf(Typed.tagged(Typed.typedClass[String], "tag1")) shouldBe true
    Typed
      .tagged(Typed.typedClass[String], "tag1")
      .canBeSubclassOf(Typed.tagged(Typed.typedClass[String], "tag2")) shouldBe false
    Typed
      .tagged(Typed.typedClass[String], "tag1")
      .canBeSubclassOf(Typed.tagged(Typed.typedClass[Integer], "tag1")) shouldBe false
    Typed.tagged(Typed.typedClass[String], "tag1").canBeSubclassOf(Typed.typedClass[String]) shouldBe true
    Typed.typedClass[String].canBeSubclassOf(Typed.tagged(Typed.typedClass[String], "tag1")) shouldBe false
  }

  test("determine if can be subclass for null") {
    TypedNull.canBeSubclassOf(Typed[Int]) shouldBe true
    TypedNull.canBeSubclassOf(Typed.fromInstance(4)) shouldBe false
    TypedNull.canBeSubclassOf(TypedNull) shouldBe true
    Typed[String].canBeSubclassOf(TypedNull) shouldBe false
  }

  test("should deeply extract typ parameters") {
    inside(Typed.fromDetailedType[Option[Map[String, Int]]]) {
      case TypedClass(optionClass, mapTypeArg :: Nil) if optionClass == classOf[Option[Any]] =>
        inside(mapTypeArg) {
          case TypedClass(optionClass, keyTypeArg :: valueTypeArg :: Nil) if optionClass == classOf[Map[Any, Any]] =>
            inside(keyTypeArg) { case TypedClass(keyClass, Nil) =>
              keyClass shouldBe classOf[String]
            }
            inside(valueTypeArg) { case TypedClass(keyClass, Nil) =>
              keyClass shouldBe classOf[java.lang.Integer]
            }
        }
    }
  }

  test("determine if can be subclass for object with value") {
    Typed.fromInstance(45).canBeSubclassOf(Typed.typedClass[Long]) shouldBe true
    Typed.fromInstance(29).canBeSubclassOf(Typed.typedClass[String]) shouldBe false
    Typed.fromInstance(78).canBeSubclassOf(Typed.fromInstance(78)) shouldBe true
    Typed.fromInstance(12).canBeSubclassOf(Typed.fromInstance(15)) shouldBe false
    Typed.fromInstance(41).canBeSubclassOf(Typed.fromInstance("t")) shouldBe false
    Typed.typedClass[String].canBeSubclassOf(Typed.fromInstance("t")) shouldBe true
  }

  test("determine if can be subclass for object with value - use conversion") {
    Typed.fromInstance("2007-12-03").canBeSubclassOf(Typed.typedClass[LocalDate]) shouldBe true
    Typed.fromInstance("2007-12-03T10:15:30").canBeSubclassOf(Typed.typedClass[LocalDateTime]) shouldBe true

    Typed.fromInstance("2007-12-03-qwerty").canBeSubclassOf(Typed.typedClass[LocalDate]) shouldBe false
    Typed.fromInstance("2007-12-03").canBeSubclassOf(Typed.typedClass[Currency]) shouldBe false
  }

  test("determinate if can be superclass for objects with value") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance(65), Typed.fromInstance(65)) shouldBe Typed
      .fromInstance(65)
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance(91), Typed.fromInstance(35)) shouldBe Typed
      .typedClass[Int]
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance("t"), Typed.fromInstance(32)) shouldBe Typed(
      Set.empty
    )
  }

  test("should calculate supertype for objects with value when strict type checking is on") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance(65), Typed.fromInstance(65)) shouldBe Typed
      .fromInstance(65)
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance(91), Typed.fromInstance(35)) shouldBe Typed
      .typedClass[Int]
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance("t"), Typed.fromInstance(32)) shouldBe Typed(
      Set.empty
    )
  }

  test("should calculate supertype for null") {
    implicit val toSupertypePromotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ToSupertype
    CommonSupertypeFinder.Union.commonSupertype(TypedNull, TypedNull) shouldBe TypedNull
    CommonSupertypeFinder.Union.commonSupertype(TypedNull, Typed[String]) shouldBe Typed[String]
    CommonSupertypeFinder.Union.commonSupertype(Typed[Int], TypedNull) shouldBe Typed[Int]

    // Literal types should have their values discarded. Otherwise expression
    // "true ? 5 : null" would have type Integer{5}.
    CommonSupertypeFinder.Union.commonSupertype(TypedNull, Typed.fromInstance(5)) shouldBe Typed[Int]
    CommonSupertypeFinder.Union.commonSupertype(Typed.fromInstance("t"), TypedNull) shouldBe Typed[String]
  }

  test("should not display too long data") {
    Typed.fromInstance("1234.1234.12").display shouldBe "String(1234.1234.12)"
    Typed.fromInstance("1234.1234.1234").display shouldBe "String(1234.1234.1234)"
    Typed.fromInstance("1234.1234.1234.1").display shouldBe "String(1234.1234.12...)"
  }

  test("should display fields in order") {
    for (keys <- List("a", "b", "c", "d").permutations) {
      typeMap(keys.map(_ -> Typed[String]): _*).display shouldBe "Record{a: String, b: String, c: String, d: String}"
    }
  }

  test("should correctly calculate union of types") {
    Typed(Set(Typed[Int], Typed[String])) shouldBe
      TypedUnion(Set(Typed.typedClass[Int], Typed.typedClass[String]))
    Typed(Set(Typed[Long], Typed(Set(Typed[Int], Typed[Long], Typed[String])))) shouldBe
      TypedUnion(Set(Typed.typedClass[Int], Typed.typedClass[Long], Typed.typedClass[String]))
    Typed(Set(Typed[Double], Unknown)) shouldBe Unknown
    Typed(Set(Typed[String])) shouldBe Typed[String]
    Typed(Set(Typed[Int], TypedNull)) shouldBe Typed[Int]
  }

  test("should correctly create typed arrays from classes") {
    Typed(classOf[Array[Object]]) shouldEqual Typed.fromDetailedType[Array[Object]]
    Typed(classOf[Array[Int]]) shouldEqual Typed.fromDetailedType[Array[Int]]
    Typed(classOf[Array[String]]) shouldEqual Typed.fromDetailedType[Array[String]]
  }

  test("should correctly handle standard java collections") {
    Typed(classOf[java.util.List[_]]) shouldEqual Typed.fromDetailedType[java.util.List[Any]]
    Typed(classOf[java.util.Map[_, _]]) shouldEqual Typed.fromDetailedType[java.util.Map[Any, Any]]
    an[IllegalArgumentException] shouldBe thrownBy {
      Typed.genericTypeClass(classOf[java.util.List[_]], List.empty)
    }
    an[IllegalArgumentException] shouldBe thrownBy {
      Typed.genericTypeClass(classOf[java.util.Map[_, _]], List.empty)
    }
  }

  test("should correctly handle type aliases") {
    Typed.fromDetailedType[StringKeyMap[Integer]] shouldEqual Typed.fromDetailedType[java.util.Map[String, Integer]]
  }

  test("should fallback to object type when looking for object supertype") {
    CommonSupertypeFinder.FallbackToObjectType.commonSupertype(
      TypedObjectTypingResult(Map.empty),
      Typed.fromDetailedType[java.util.Map[String, Any]]
    ) shouldEqual Typed.fromDetailedType[java.util.Map[String, Any]]
    CommonSupertypeFinder.FallbackToObjectType.commonSupertype(
      TypedTaggedValue(Typed.typedClass[String], "foo"),
      TypedTaggedValue(Typed.typedClass[String], "bar")
    ) shouldEqual Typed[String]
    CommonSupertypeFinder.FallbackToObjectType.commonSupertype(
      TypedObjectWithValue(Typed.typedClass[String], "foo"),
      TypedObjectWithValue(Typed.typedClass[String], "bar")
    ) shouldEqual Typed[String]
    CommonSupertypeFinder.FallbackToObjectType.commonSupertype(Typed[Int], Typed[Long]) shouldEqual Typed[Number]
  }

  type StringKeyMap[V] = java.util.Map[String, V]

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
