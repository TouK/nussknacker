package pl.touk.nussknacker.engine.api.typed

import cats.implicits._
import cats.data.NonEmptyList
import org.scalatest.{Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing._

class TypingResultErrorMessagesSpec extends AnyFunSuite with Matchers with OptionValues with Inside {

  private def typeMap(args: (String, TypingResult)*) = Typed.record(args)

  private def list(arg: TypingResult) = Typed.genericTypeClass[java.util.List[_]](List(arg))

  import ImplicitConversionDeterminer.canBeConvertedTo

  test("determine if can be subclass for typed object") {

    canBeConvertedTo(
      typeMap(
        "field1" -> Typed[String],
        "field2" -> Typed[Int],
        "field3" -> list(typeMap("field2a" -> Typed[String], "field3" -> Typed[Int])),
        "field5" -> list(typeMap("field2" -> Typed[String]))
      ),
      typeMap(
        "field2" -> Typed[String],
        "field3" -> Typed[String],
        "field4" -> Typed[String],
        "field5" -> list(typeMap("field2" -> Typed[String]))
      )
    ) shouldBe NonEmptyList
      .of(
        "Field 'field2' is of the wrong type. Expected: Integer, actual: String",
        "Field 'field3' is of the wrong type. Expected: List[Record{field2a: String, field3: Integer}], actual: String",
        "Field 'field4' is lacking"
      )
      .invalid

    canBeConvertedTo(
      typeMap("field1" -> list(typeMap("field2a" -> Typed[String], "field3" -> Typed[Int]))),
      typeMap("field1" -> list(typeMap("field2" -> Typed[String])))
    ) shouldBe NonEmptyList
      .of(
        "Map[String,List[Record{field2a: String, field3: Integer}]] cannot be converted to Map[String,List[Record{field2: String}]]"
      )
      .invalid
  }

  test("determine if can be subclass for class") {
    canBeConvertedTo(Typed.fromDetailedType[Set[BigDecimal]], Typed.fromDetailedType[Set[String]]) shouldBe
      "Set[BigDecimal] cannot be converted to Set[String]".invalidNel
  }

  test("determine if can be subclass for tagged value") {
    canBeConvertedTo(
      Typed.tagged(Typed.typedClass[String], "tag1"),
      Typed.tagged(Typed.typedClass[String], "tag2")
    ) shouldBe
      "Tagged values have unequal tags: tag1 and tag2".invalidNel

    canBeConvertedTo(Typed.typedClass[String], Typed.tagged(Typed.typedClass[String], "tag1")) shouldBe
      "The type is not a tagged value".invalidNel
  }

  test("determine if can be subclass for object with value") {
    canBeConvertedTo(Typed.fromInstance(2), Typed.fromInstance(3)) shouldBe
      "Types with value have different values: 2 and 3".invalidNel
  }

  test("determine if can be subclass for null") {
    canBeConvertedTo(Typed[String], TypedNull) shouldBe
      "No type can be subclass of Null".invalidNel
    canBeConvertedTo(TypedNull, Typed.fromInstance(1)) shouldBe
      "Null cannot be subclass of type with value".invalidNel
  }

}
