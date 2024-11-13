package pl.touk.nussknacker.engine.api.typed

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing._

import java.time.{LocalDate, LocalDateTime}
import java.util
import java.util.Currency

// TODO: clean-up, split tests for Intersection (equals operator), Default (ternary), number promotion, can be subclass etc.
class TypingResultSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with Inside
    with ScalaCheckDrivenPropertyChecks
    with LazyLogging {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0)

  private val intersectionSuperTypeFinder = CommonSupertypeFinder.Intersection

  private def typeMap(args: (String, TypingResult)*) = Typed.record(args)

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
    Typed
      .record(
        Map(
          "foo" -> Typed[String],
          "bar" -> Typed.record(Map.empty[String, TypingResult])
        )
      )
      .runtimeObjType
      .params(1) shouldEqual Unknown

    Typed.record(Map.empty[String, TypingResult]).runtimeObjType.params(1) shouldEqual Unknown
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
    Typed
      .fromDetailedType[java.util.List[BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.List[BigDecimal]]) shouldBe true
    Typed
      .fromDetailedType[java.util.List[BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.List[Number]]) shouldBe true
    Typed
      .fromDetailedType[java.util.List[Number]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.List[BigDecimal]]) shouldBe false

    Typed
      .fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]) shouldBe true
    Typed
      .fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[BigDecimal, Number]]) shouldBe true
    Typed
      .fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[Number, Number]]) shouldBe false
    Typed
      .fromDetailedType[java.util.Map[Number, Number]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]) shouldBe false
    Typed
      .fromDetailedType[java.util.Map[Number, BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]) shouldBe false
    Typed
      .fromDetailedType[java.util.Map[BigDecimal, Number]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]) shouldBe false
    Typed
      .fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[_, BigDecimal]]) shouldBe true
    Typed
      .fromDetailedType[java.util.Map[_, BigDecimal]]
      .canBeSubclassOf(Typed.fromDetailedType[java.util.Map[BigDecimal, BigDecimal]]) shouldBe true

    // For arrays it might be tricky
    Typed.fromDetailedType[Array[BigDecimal]].canBeSubclassOf(Typed.fromDetailedType[Array[BigDecimal]]) shouldBe true
    Typed.fromDetailedType[Array[BigDecimal]].canBeSubclassOf(Typed.fromDetailedType[Array[Number]]) shouldBe true
    Typed.fromDetailedType[Array[Number]].canBeSubclassOf(Typed.fromDetailedType[Array[BigDecimal]]) shouldBe false
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
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[String], Typed[Boolean]) shouldBe empty
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[String], Typed[Int]) shouldBe empty
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[String], Typed[String]).value shouldEqual Typed[String]
    intersectionSuperTypeFinder.commonSupertypeOpt(
      Typed[Float],
      Typed.tagged(Typed.typedClass[Float], "example")
    ) shouldBe empty
    intersectionSuperTypeFinder.commonSupertypeOpt(
      Typed.tagged(Typed.typedClass[Float], "example"),
      Typed[Float]
    ) shouldBe empty
  }

  test("number types promotion") {
    NumberTypesPromotionStrategy.ForMathOperation.promote(
      Typed[java.lang.Integer],
      Typed[java.lang.Double]
    ) shouldEqual Typed[java.lang.Double]
    NumberTypesPromotionStrategy.ForMathOperation.promote(Typed[Int], Typed[Double]) shouldEqual Typed[java.lang.Double]
    NumberTypesPromotionStrategy.ForMathOperation.promote(Typed[Int], Typed[Long]) shouldEqual Typed[java.lang.Long]
    NumberTypesPromotionStrategy.ForMathOperation.promote(Typed[Float], Typed[Long]) shouldEqual Typed[java.lang.Float]
    NumberTypesPromotionStrategy.ForMathOperation.promote(
      Typed.fromInstance(1),
      Typed.fromInstance(2)
    ) shouldEqual Typed[Int]
  }

  test("find special types") {
    intersectionSuperTypeFinder.commonSupertypeOpt(Unknown, Unknown).value shouldEqual Unknown
    intersectionSuperTypeFinder.commonSupertypeOpt(Unknown, Typed[Long]).value shouldEqual Unknown
    CommonSupertypeFinder.Default.commonSupertype(
      Typed.record(Map("foo" -> Typed[String], "bar" -> Typed[Int], "baz" -> Typed[String])),
      Typed.record(Map("foo" -> Typed[String], "bar" -> Typed[Long], "baz2" -> Typed[String]))
    ) shouldEqual
      Typed.record(
        Map("foo" -> Typed[String], "bar" -> Typed[Number], "baz" -> Typed[String], "baz2" -> Typed[String])
      )

    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.record(Map("foo" -> Typed[String])),
        Typed.record(Map("foo" -> Typed[Long]))
      )
      .value shouldEqual Typed.record(Map.empty[String, TypingResult])
  }

  test("find common supertype for complex types with inheritance in classes hierarchy") {
    import ClassHierarchy._
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Pet]).value shouldEqual Typed[Pet]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Cat]).value shouldEqual Typed[Pet]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Cactus]) shouldBe empty

    intersectionSuperTypeFinder
      .commonSupertypeOpt(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat])
      .value shouldEqual Typed[Pet]
  }

  test("find common supertype for complex types with inheritance in interfaces hierarchy") {
    import InterfaceHierarchy._
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Pet]).value shouldEqual Typed[Pet]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Cat]).value shouldEqual Typed[Pet]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Cactus]) shouldBe empty

    intersectionSuperTypeFinder
      .commonSupertypeOpt(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat])
      .value shouldEqual Typed[Pet]
  }

  test("find common supertype for complex types with inheritance in mixins hierarchy") {
    import HierarchyInMixins._
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Pet]).value shouldEqual Typed[Pet]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Cat]).value shouldEqual Typed[Pet]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Dog], Typed[Cactus]) shouldBe empty

    intersectionSuperTypeFinder
      .commonSupertypeOpt(Typed(Typed[Dog], Typed[Cactus]), Typed[Cat])
      .value shouldEqual Typed[Pet]
  }

  test("common supertype with generics") {
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.fromDetailedType[Comparable[Number]],
        Typed.fromDetailedType[Integer]
      )
      .value shouldEqual Typed.fromDetailedType[Comparable[Number]]
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.fromDetailedType[Integer],
        Typed.fromDetailedType[Comparable[Number]]
      )
      .value shouldEqual Typed.fromDetailedType[Comparable[Number]]
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.fromDetailedType[util.List[Integer]],
        Typed.fromDetailedType[util.List[Number]]
      )
      .value shouldEqual Typed.fromDetailedType[util.List[Number]]
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.fromDetailedType[util.List[Integer]],
        Typed.fromDetailedType[util.Collection[Number]]
      )
      .value shouldEqual Typed.fromDetailedType[util.Collection[Number]]
    // below weird examples which will work
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.fromDetailedType[util.List[Number]],
        Typed.fromDetailedType[util.Collection[Integer]]
      )
      .value shouldEqual Typed.fromDetailedType[util.Collection[Number]]
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        Typed.fromDetailedType[util.List[String]],
        Typed.fromDetailedType[util.Collection[Integer]]
      ) shouldBe empty
    CommonSupertypeFinder.Default
      .commonSupertype(
        Typed.fromDetailedType[util.List[String]],
        Typed.fromDetailedType[util.Collection[Integer]]
      ) shouldEqual Typed.genericTypeClass[util.Collection[_]](List(Unknown))
    val tupleIterable = Typed.fromDetailedType[Iterable[(String, Integer)]]
    val map           = Typed.fromDetailedType[Map[String, Integer]]
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        map,
        tupleIterable
      )
      .value shouldEqual tupleIterable
    intersectionSuperTypeFinder
      .commonSupertypeOpt(
        tupleIterable,
        map,
      )
      .value shouldEqual tupleIterable
  }

  test("common supertype for not matching classes") {
    import ClassHierarchy._
    intersectionSuperTypeFinder.commonSupertypeOpt(
      Typed.tagged(Typed.typedClass[Dog], "dog"),
      Typed[Cactus]
    ) shouldBe empty
    intersectionSuperTypeFinder.commonSupertypeOpt(
      Typed[Cactus],
      Typed.tagged(Typed.typedClass[Dog], "dog")
    ) shouldBe empty
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
      case TypedClass(optionClass, mapTypeArg :: Nil, _) if optionClass == classOf[Option[Any]] =>
        inside(mapTypeArg) {
          case TypedClass(optionClass, keyTypeArg :: valueTypeArg :: Nil, _) if optionClass == classOf[Map[Any, Any]] =>
            inside(keyTypeArg) { case TypedClass(keyClass, Nil, _) =>
              keyClass shouldBe classOf[String]
            }
            inside(valueTypeArg) { case TypedClass(keyClass, Nil, _) =>
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
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed.fromInstance(65), Typed.fromInstance(65)).value shouldBe Typed
      .fromInstance(65)
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed.fromInstance(91), Typed.fromInstance(35)).value shouldBe Typed
      .typedClass[Int]
  }

  test("should calculate supertype for null") {
    intersectionSuperTypeFinder.commonSupertypeOpt(TypedNull, TypedNull).value shouldBe TypedNull
    intersectionSuperTypeFinder.commonSupertypeOpt(TypedNull, Typed[String]).value shouldBe Typed[String]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed[Int], TypedNull).value shouldBe Typed[Int]

    // Literal types should have their values discarded. Otherwise expression
    // "true ? 5 : null" would have type Integer{5}.
    intersectionSuperTypeFinder.commonSupertypeOpt(TypedNull, Typed.fromInstance(5)).value shouldBe Typed[Int]
    intersectionSuperTypeFinder.commonSupertypeOpt(Typed.fromInstance("t"), TypedNull).value shouldBe Typed[String]
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
    Typed(Typed[Long], Typed[Int], Typed[Long], Typed[String]) shouldBe
      Typed(Typed.typedClass[Int], Typed.typedClass[Long], Typed.typedClass[String])
    Typed(Typed[Long], Typed(Typed[Int], Typed[Long], Typed[String])) shouldBe
      Typed(Typed.typedClass[Int], Typed.typedClass[Long], Typed.typedClass[String])
    Typed(Typed[Double], Unknown) shouldBe Unknown
    Typed(Typed[String], Typed[String]) shouldBe Typed[String]
    Typed(Typed[Int], TypedNull) shouldBe Typed[Int]
    Typed(TypedNull, TypedNull) shouldBe TypedNull
    // TODO: we could fold it to Typed[Number] - see TODO in Typed.apply
    Typed(Typed[Number], Typed[Int]) shouldBe Typed(Typed[Number], Typed[Int])
    Typed(Typed[Int], Typed[Number]) shouldBe Typed(Typed[Int], Typed[Number])
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
    CommonSupertypeFinder.Default.commonSupertype(
      Typed.record(Map.empty),
      Typed.fromDetailedType[java.util.Map[String, Any]]
    ) shouldEqual Typed.fromDetailedType[java.util.Map[String, Any]]
    CommonSupertypeFinder.Default.commonSupertype(
      TypedTaggedValue(Typed.typedClass[String], "foo"),
      TypedTaggedValue(Typed.typedClass[String], "bar")
    ) shouldEqual Typed[String]
    CommonSupertypeFinder.Default.commonSupertype(
      TypedObjectWithValue(Typed.typedClass[String], "foo"),
      TypedObjectWithValue(Typed.typedClass[String], "bar")
    ) shouldEqual Typed[String]
    CommonSupertypeFinder.Default.commonSupertype(Typed[Int], Typed[Long]) shouldEqual Typed[Number]
    CommonSupertypeFinder.Default.commonSupertype(
      Typed.record(Map("foo" -> Typed[String])),
      Typed.record(Map("foo" -> Typed[Boolean])),
    ) shouldEqual Typed.record(Map("foo" -> Unknown))
    CommonSupertypeFinder.Intersection
      .commonSupertypeOpt(
        Typed.record(Map("foo" -> Typed[String])),
        Typed.record(Map("foo" -> Typed[Boolean])),
      )
      .value shouldEqual Typed.record(Map.empty)
  }

  test("generator based common super type for one type without unions - check type equality") {
    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures(unions = false))) { input =>
      logger.trace(s"Checking: ${input.display}")
      withClue(s"Input: ${input.display};") {

        input.canBeSubclassOf(input) shouldBe true
        val superType = CommonSupertypeFinder.Default.commonSupertype(input, input)
        withClue(s"Supertype: ${superType.display};") {
          superType shouldEqual input
        }
      }
    }
  }

  test("generator based common super type for one type with unions - check if class can by subclass of superclass") {
    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures.All)) { input =>
      logger.trace(s"Checking: ${input.display}")
      withClue(s"Input: ${input.display};") {

        input.canBeSubclassOf(input) shouldBe true
        val superType = CommonSupertypeFinder.Default.commonSupertype(input, input)
        withClue(s"Supertype: ${superType.display};") {
          // We generate combinations of types co we can only check if input type is a subclass of super type
          input.canBeSubclassOf(superType)
        }
      }
    }
  }

  test(
    "generator based common super type for two types with unions - check if both classes can by subclass of superclass"
  ) {
    forAll(
      TypingResultGen.typingResultGen(EnabledTypedFeatures.All),
      TypingResultGen.typingResultGen(EnabledTypedFeatures.All)
    ) { (first, second) =>
      logger.trace(s"Checking supertype of: ${first.display} and ${second.display}")
      withClue(s"Input: ${first.display}; ${second.display};") {

        first.canBeSubclassOf(first) shouldBe true
        second.canBeSubclassOf(second) shouldBe true
        val superType = CommonSupertypeFinder.Default.commonSupertype(first, second)
        withClue(s"Supertype: ${superType.display};") {
          first.canBeSubclassOf(superType)
          second.canBeSubclassOf(superType)
        }
      }
    }
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
