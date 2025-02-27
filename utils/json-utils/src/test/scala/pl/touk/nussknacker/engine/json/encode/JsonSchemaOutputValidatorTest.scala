package pl.touk.nussknacker.engine.json.encode

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.typed.typing.Typed._
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder

import scala.collection.immutable.ListMap

class JsonSchemaOutputValidatorTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {
  val strictValidator = new JsonSchemaOutputValidator(ValidationMode.strict)
  val laxValidator    = new JsonSchemaOutputValidator(ValidationMode.lax)

  test("should validate against 'map string to Any' schema") {
    val mapStringToAnySchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid"),
      (typedClass[String], false),
      (
        genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[Integer], Unknown)),
        false
      ), // not tested in functional tests
      (genericTypeClass(classOf[Map[_, _]], List(typedClass[String], Unknown)), false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), true),
      (
        genericTypeClass(
          classOf[java.util.Map[_, _]],
          List(
            typedClass[String],
            Typed.record(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String]))
          )
        ),
        true
      ),
      (genericTypeClass(classOf[java.util.List[_]], List(typedClass[String])), false),
      (Typed.record(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])), true),
      (Typed.record(ListMap("foo" -> Unknown)), true), // not tested in functional tests
    )

    forAll(testData) { (typing: TypingResult, isValid: Boolean) =>
      strictValidator.validate(typing, mapStringToAnySchema).isValid shouldBe isValid
    }
  }

  test("should validate against 'map string to string' schema") {
    val mapStringToStringSchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "additionalProperties": {
        |    "type": "string"
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid"),
      (typedClass[String], false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false),
      (
        genericTypeClass(
          classOf[java.util.Map[_, _]],
          List(typedClass[String], Typed.record(ListMap("foo" -> Typed[String], "baz" -> Typed[String])))
        ),
        false
      ),
      (
        Typed.record(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])),
        false
      ),
      (Typed.record(ListMap("foo" -> Typed[String], "baz" -> Typed[String])), true),
      (Typed.record(ListMap("foo" -> Unknown)), false),
    )

    forAll(testData) { (typing: TypingResult, isValid: Boolean) =>
      strictValidator.validate(typing, mapStringToStringSchema).isValid shouldBe isValid
    }
  }

  test("should validate against 'map string to union' schema") {
    val mapStringToStringOrIntSchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "additionalProperties": {
        |    "type": ["string", "integer"]
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid"),
      (typedClass[String], false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), true),
      (
        genericTypeClass(
          classOf[java.util.Map[_, _]],
          List(typedClass[String], Typed.record(ListMap("foo" -> Typed[String], "baz" -> Typed[String])))
        ),
        false
      ),
      (Typed.record(ListMap("foo" -> Typed[String], "bar" -> Typed[Integer], "baz" -> Typed[String])), true),
      (
        Typed.record(
          ListMap(
            "foo" -> Typed[String],
            "bar" -> Typed[Integer],
            "baz" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[String]))
          )
        ),
        false
      ),
      (Typed.record(ListMap("foo" -> Typed[String], "baz" -> Typed[String])), true),
    )

    forAll(testData) { (typing: TypingResult, isValid: Boolean) =>
      strictValidator.validate(typing, mapStringToStringOrIntSchema).isValid shouldBe isValid
    }
  }

  test("validate against 'additionalProperties with patternProperties' schema") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "additionalProperties": {
        |    "type": ["string", "null"]
        |  },
        |  "patternProperties": {
        |    "_int$": {
        |      "oneOf": [
        |        { "type": "integer" },
        |        { "type": "null" }
        |      ]
        |    }
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid for strict", "is valid for lax"),
      (Typed.record(ListMap("foo" -> Typed[String], "foo_int" -> Typed[Integer])), true, true),
      (Typed.record(ListMap("foo" -> Typed[Integer])), false, false),
      (Typed.record(ListMap("foo_int" -> Typed[String])), false, false),
      (Typed.record(ListMap("foo_int" -> Unknown)), false, true),
      (Typed.record(ListMap("foo" -> Unknown)), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Boolean])), false, false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false, true),
    )

    forAll(testData) { (typing: TypingResult, isValidForStrict: Boolean, isValidForLax: Boolean) =>
      strictValidator.validate(typing, schema).isValid shouldBe isValidForStrict
      laxValidator.validate(typing, schema).isValid shouldBe isValidForLax
    }
  }

  test("validate against 'patternProperties without additionalProperties' schema") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "patternProperties": {
        |    "_int$": {
        |      "oneOf": [
        |        { "type": "integer" },
        |        { "type": "null" }
        |      ]
        |    }
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid for strict", "is valid for lax"),
      (Typed.record(ListMap("foo" -> Typed[String], "foo_int" -> Typed[Integer])), true, true),
      (Typed.record(ListMap("foo" -> Typed[Integer])), true, true),
      (Typed.record(ListMap("foo_int" -> Typed[String])), false, false),
      (Typed.record(ListMap("foo_int" -> Unknown)), false, true),
      (Typed.record(ListMap("foo" -> Unknown)), true, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Boolean])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false, true),
    )

    forAll(testData) { (typing: TypingResult, isValidForStrict: Boolean, isValidForLax: Boolean) =>
      strictValidator.validate(typing, schema).isValid shouldBe isValidForStrict
      laxValidator.validate(typing, schema).isValid shouldBe isValidForLax
    }
  }

  test("validate against 'explicit properties with patternProperties and additionalProperties' schema") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "booleanProperty": {
        |      "type": ["boolean", "null"]
        |    }
        |  },
        |  "additionalProperties": {
        |    "type": ["string", "null"]
        |  },
        |  "patternProperties": {
        |    "_int$": {
        |      "oneOf": [
        |        { "type": "integer" },
        |        { "type": "null" }
        |      ]
        |    }
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid for strict", "is valid for lax"),
      (
        Typed.record(
          ListMap("foo" -> Typed[String], "foo_int" -> Typed[Integer], "booleanProperty" -> Typed[Boolean])
        ),
        true,
        true
      ),
      (Typed.record(ListMap("foo" -> Typed[String], "foo_int" -> Typed[Integer])), false, true),
      (Typed.record(ListMap("foo" -> Typed[Integer])), false, false),
      (Typed.record(ListMap("foo_int" -> Typed[String])), false, false),
      (Typed.record(ListMap("foo_int" -> Unknown)), false, true),
      (Typed.record(ListMap("foo" -> Unknown)), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Boolean])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false, true),
    )

    forAll(testData) { (typing: TypingResult, isValidForStrict: Boolean, isValidForLax: Boolean) =>
      strictValidator.validate(typing, schema).isValid shouldBe isValidForStrict
      laxValidator.validate(typing, schema).isValid shouldBe isValidForLax
    }
  }

  test("validate against 'additionalProperties=false' schema") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "explicitProperty": {
        |      "type": ["string", "null"]
        |    }
        |  },
        |  "additionalProperties": false
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid for strict", "is valid for lax"),
      (Typed.record(ListMap("explicitProperty" -> Typed[String])), true, true),
      (Typed.record(ListMap("explicitProperty" -> Typed[Integer])), false, false),
      (
        Typed.record(ListMap("explicitProperty" -> Typed[String], "additionalProperty" -> Typed[String])),
        false,
        true
      ),
      (Typed.record(ListMap[String, TypingResult]()), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false, false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false, true),
    )

    forAll(testData) { (typing: TypingResult, isValidForStrict: Boolean, isValidForLax: Boolean) =>
      strictValidator.validate(typing, schema).isValid shouldBe isValidForStrict
      laxValidator.validate(typing, schema).isValid shouldBe isValidForLax
    }
  }

  test("validate against schema with required props") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "explicitRequiredProperty": {
        |      "type": ["string", "null"]
        |    }
        |  },
        |  "required": [
        |    "explicitRequiredProperty"
        |  ]
        |}""".stripMargin)

    val testData = Table(
      ("typing", "is valid for strict", "is valid for lax"),
      (Typed.record(ListMap("explicitRequiredProperty" -> Typed[String])), true, true),
      (Typed.record(ListMap("explicitRequiredProperty" -> Typed[Integer])), false, false),
      (
        Typed.record(
          ListMap("explicitRequiredProperty" -> Typed[String], "additionalProperty" -> Typed[String])
        ),
        true,
        true
      ),
      (Typed.record(ListMap[String, TypingResult]()), false, false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[String])), false, true),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], typedClass[Integer])), false, false),
      (genericTypeClass(classOf[java.util.Map[_, _]], List(typedClass[String], Unknown)), false, true),
    )

    forAll(testData) { (typing: TypingResult, isValidForStrict: Boolean, isValidForLax: Boolean) =>
      strictValidator.validate(typing, schema).isValid shouldBe isValidForStrict
      laxValidator.validate(typing, schema).isValid shouldBe isValidForLax
    }
  }

  test("works for empty maps") {
    val emptyMapSchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "additionalProperties": {}
        |}""".stripMargin)

    def validate(typing: TypingResult) = new JsonSchemaOutputValidator(ValidationMode.strict)
      .validate(typing, emptyMapSchema)

    validate(Typed.record(ListMap[String, TypingResult]())) shouldBe Symbol("valid")
    validate(Typed.record(ListMap("stringProp" -> Typed[String]))) shouldBe Symbol("valid")
    validate(
      Typed.record(ListMap("someMap" -> Typed.record(ListMap("any" -> Typed[String]))))
    ) shouldBe Symbol("valid")

  }

}
