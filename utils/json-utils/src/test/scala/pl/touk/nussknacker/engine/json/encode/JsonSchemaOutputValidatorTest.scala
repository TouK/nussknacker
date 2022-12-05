package pl.touk.nussknacker.engine.json.encode

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing.Typed._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder

import scala.collection.immutable.ListMap

class JsonSchemaOutputValidatorTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {
  val validator = new JsonSchemaOutputValidator(ValidationMode.strict)

  test("should validate against 'map string to Any' schema") {
    val mapStringToAnySchema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid"),
      (typedClass[String], false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[Integer], Unknown)), false),  //not tested in functional tests
      (genericTypeClass(classOf[Map[_, _]], List(typedClass[String], Unknown)), false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], TypedObjectTypingResult(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])))), true),
      (genericTypeClass(classOf[java.util.List[_]], List(typedClass[String])), false),
      (TypedObjectTypingResult(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])), true),
      (TypedObjectTypingResult(ListMap("foo" -> Unknown)), true), //not tested in functional tests
    )

    forAll(testData) { (typing: TypingResult, isValid: Boolean) =>
      validator.validateTypingResultAgainstSchema(typing, mapStringToAnySchema).isValid shouldBe isValid
    }
  }

  test("should validate against 'map string to string' schema") {
    val mapStringToStringSchema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "additionalProperties": {
        |     "type": "string"
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid"),
      (typedClass[String], false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], TypedObjectTypingResult(ListMap("foo" -> Typed[String], "baz" -> Typed[String])))), false),
      (TypedObjectTypingResult(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])), false),
      (TypedObjectTypingResult(ListMap("foo" -> Typed[String], "baz" -> Typed[String])), true),
      (TypedObjectTypingResult(ListMap("foo" -> Unknown)), false),
    )

    forAll(testData) { (typing: TypingResult, isValid: Boolean) =>
      validator.validateTypingResultAgainstSchema(typing, mapStringToStringSchema).isValid shouldBe isValid
    }
  }

  test("should validate against 'map string to union' schema") {
    val mapStringToStringOrIntSchema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "additionalProperties": {
        |     "type": ["string", "integer"]
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid"),
      (typedClass[String], false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], TypedObjectTypingResult(ListMap("foo" -> Typed[String], "baz" -> Typed[String])))), false),
      (TypedObjectTypingResult(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])), true),
      (TypedObjectTypingResult(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[String])))), false),
      (TypedObjectTypingResult(ListMap("foo" -> Typed[String], "baz" -> Typed[String])), true),
    )

    forAll(testData) { (typing: TypingResult, isValid: Boolean) =>
      validator.validateTypingResultAgainstSchema(typing, mapStringToStringOrIntSchema).isValid shouldBe isValid
    }
  }

  test("works for empty maps") {
    val emptyMapSchema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "additionalProperties": {}
        |}""".stripMargin)

    def validate(typing: TypingResult) = new JsonSchemaOutputValidator(ValidationMode.strict)
      .validateTypingResultAgainstSchema(typing, emptyMapSchema)

    validate(TypedObjectTypingResult(ListMap[String, TypingResult]())) shouldBe 'valid
    validate(TypedObjectTypingResult(ListMap("stringProp" -> Typed[String]))) shouldBe 'invalid
    validate(TypedObjectTypingResult(ListMap("someMap" -> TypedObjectTypingResult(ListMap("any" -> Typed[String]))))) shouldBe 'valid

  }

}
