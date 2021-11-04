package pl.touk.nussknacker.engine.avro

import cats.data.Validated.Invalid
import cats.data.ValidatedNel
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.avro.AvroDefaultExpressionExtractor.AvroDefaultToSpELExpressionError
import pl.touk.nussknacker.engine.graph.expression.Expression

class AvroDefaultExpressionExtractorTest extends FunSuite with Matchers {
  import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

  test("string default") {
    val stringField = schema.getFields.get(0)
    val expression = new AvroDefaultExpressionExtractor(stringField, handleNotSupported = false).toExpression

    verify(expression) { _ shouldBe Some(asSpelExpression("'stringDefault'")) }
  }

  test("null default") {
    val nullableStringField = schema.getFields.get(1)
    val expression = new AvroDefaultExpressionExtractor(nullableStringField, handleNotSupported = false).toExpression

    verify(expression) { _ shouldBe Some(asSpelExpression("null")) }
  }

  test("long default") {
    val longField = schema.getFields.get(2)
    val expression = new AvroDefaultExpressionExtractor(longField, handleNotSupported = false).toExpression

    verify(expression) {_ shouldBe Some(asSpelExpression("42L")) }
  }

  test("not supported record default") {
    val recordField = schema.getFields.get(3)
    val expression = new AvroDefaultExpressionExtractor(recordField, handleNotSupported = false).toExpression

    expression shouldBe Invalid(
      AvroDefaultExpressionExtractor.TypeNotSupported(recordField.schema())
    ).toValidatedNel
  }

  test("not supported record default with not supported type handling") {
    val recordField = schema.getFields.get(3)
    val expression = new AvroDefaultExpressionExtractor(recordField, handleNotSupported = true).toExpression

    verify(expression) { _ shouldBe None }
  }

  test("not supported nullable record default") {
    val nullableRecordField = schema.getFields.get(4)
    val expression = new AvroDefaultExpressionExtractor(nullableRecordField, handleNotSupported = false).toExpression

    expression shouldBe Invalid(
      AvroDefaultExpressionExtractor.TypeNotSupported(nullableRecordField.schema())
    ).toValidatedNel
  }

  private def verify(validatedExpression: ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]])(assertion: Option[Expression] => Assertion): Unit = {
    val expression = validatedExpression.valueOr(errors => throw errors.head)
    assertion(expression)
  }

  private lazy val schema =
    AvroUtils.parseSchema(
      s"""
       |{
       |  "type": "record",
       |  "name": "MyRecord",
       |  "fields": [
       |    {
       |      "name": "stringField_0",
       |      "type": "string",
       |      "default": "stringDefault"
       |    },
       |    {
       |      "name": "nullableStringField_1",
       |      "type": ["null", "string"],
       |      "default": null
       |    },
       |    {
       |      "name": "longField_2",
       |      "type": "long",
       |      "default": 42
       |    },
       |    {
       |      "name": "recordField_3",
       |      "type": {
       |        "name": "recordField",
       |        "type": "record",
       |        "fields": []
       |      },
       |      "default": {}
       |    },
       |    {
       |      "name": "nullableRecord_4",
       |      "type": ["null", {
       |        "name": "recordFieldOfUnion",
       |        "type": "record",
       |        "fields": []
       |      }],
       |      "default": null
       |    }
       |   ]
       |}
    """.stripMargin)
}
