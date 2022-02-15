package pl.touk.nussknacker.engine.api.typed

import cats.implicits._
import cats.data.NonEmptyList
import org.scalatest.{FunSuite, Inside, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.typed.typing._

class TypingResultErrorMessagesSpec extends FunSuite with Matchers with OptionValues with Inside {

  private def typeMap(args: (String, TypingResult)*) = TypedObjectTypingResult(args.toList)

  private def list(arg: TypingResult) = Typed.genericTypeClass[java.util.List[_]](List(arg))

  import CanBeSubclassDeterminer.canBeSubclassOf

  test("determine if can be subclass for typed object") {

    canBeSubclassOf(
      typeMap("field1" -> Typed[String], "field2" -> Typed[Int], "field3" -> list(typeMap("field2a" -> Typed[String], "field3" -> Typed[Int])), "field5" -> list(typeMap("field2" -> Typed[String]))),
      typeMap("field2" -> Typed[String], "field3" -> Typed[String], "field4" -> Typed[String], "field5" -> list(typeMap("field2" -> Typed[String])))
    ) shouldBe NonEmptyList.of("Field 'field2' is of the wrong type. Expected: Integer, actual: String", "Field 'field3' is of the wrong type. Expected: List[{field2a: String, field3: Integer}], actual: String", "Field 'field4' is lacking").invalid

    canBeSubclassOf(
      typeMap("field1" -> list(typeMap("field2a" -> Typed[String], "field3" -> Typed[Int]))),
      typeMap("field1" -> list(typeMap("field2" -> Typed[String])))
    ) shouldBe NonEmptyList.of("Field 'field1' is of the wrong type. Expected: List[{field2a: String, field3: Integer}], actual: List[{field2: String}]").invalid
  }


  test("determine if can be subclass for class") {
    canBeSubclassOf(Typed.fromDetailedType[Set[BigDecimal]], Typed.fromDetailedType[Set[String]]) shouldBe
      "Set[BigDecimal] cannot be converted to Set[String]".invalidNel
  }

  test("determine if can be subclass for tagged value") {
    canBeSubclassOf(Typed.tagged(Typed.typedClass[String], "tag1"), Typed.tagged(Typed.typedClass[String], "tag2")) shouldBe
      "Tagged values have unequal tags: tag1 and tag2".invalidNel

    canBeSubclassOf(Typed.typedClass[String], Typed.tagged(Typed.typedClass[String], "tag1")) shouldBe
    "The type is not a tagged value".invalidNel
  }
}
