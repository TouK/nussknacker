package pl.touk.nussknacker.engine.avro

import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.Schema
import org.scalatest.{Assertion, FunSuite, Matchers, Succeeded}
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.time.Instant
import java.util.UUID

class AvroDefaultExpressionDeterminerTest extends FunSuite with Matchers {
  import scala.collection.JavaConverters._
  import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

  test("string default") {
    verify("stringField_0")(
      { _ shouldBe Some(asSpelExpression("'stringDefault'")) },
      { _ shouldBe "stringDefault" }
    )
  }

  test("null default") {
    verifyIsNull("nullableStringField_1")
  }

  test("long default") {
    verify("longField_2")(
      { _ shouldBe Some(asSpelExpression("42L")) },
      { _ shouldBe 42L }
    )
  }

  test("not supported record default") {
    val recordField = getFieldSchema("recordField_3")
    val expression = new AvroDefaultExpressionDeterminer(handleNotSupported = false).determine(recordField)

    expression shouldBe Invalid(
      AvroDefaultExpressionDeterminer.TypeNotSupported(recordField.schema())
    ).toValidatedNel
  }

  test("not supported record default with not supported type handling") {
    val recordField = getFieldSchema("recordField_3")
    val validatedExpression = new AvroDefaultExpressionDeterminer(handleNotSupported = true).determine(recordField)
    validatedExpression shouldBe Valid(None)
  }

  test("nullable record with null default") {
    verifyIsNull("nullableRecord_4")
  }

  test("union with default of supported type") {
    verify("unionOfIntAndRecord_5")(
      { _ shouldBe Some(asSpelExpression("42")) },
      { _ shouldBe 42 }
    )
  }

  test("union with default of not supported type") {
    val unionOfRecordAndInt = getFieldSchema("unionOfRecordAndInt_6")
    val expression = new AvroDefaultExpressionDeterminer(handleNotSupported = false).determine(unionOfRecordAndInt)

    expression shouldBe Invalid(
      AvroDefaultExpressionDeterminer.TypeNotSupported(unionOfRecordAndInt.schema())
    ).toValidatedNel
  }

  test("uuid default") {
    verify("uuidField_7")(
      { _ shouldBe Some(asSpelExpression("T(java.util.UUID).fromString('00000000-0000-0000-0000-000000000000')")) },
      { _ shouldBe UUID.fromString("00000000-0000-0000-0000-000000000000") }
    )
  }

  test("timestamp-millis default") {
    verify("timestampMillisField_8")(
      { _ shouldBe Some(asSpelExpression("T(java.time.Instant).ofEpochMilli(0L)")) },
      { _ shouldBe Instant.ofEpochMilli(0L) }
    )
  }

  private def verify(fieldName: String)(expressionAssertion: Option[Expression] => Assertion,
                                        valueAssertion: AnyRef => Assertion = _ => Succeeded): Unit = {
    val fieldSchema = getFieldSchema(fieldName)
    val validatedExpression = new AvroDefaultExpressionDeterminer(handleNotSupported = false).determine(fieldSchema)
    val expression = validatedExpression.valueOr(errors => throw errors.head)
    expressionAssertion(expression)

    val fieldValue = record.get(fieldName)
    valueAssertion(fieldValue)
  }

  private def verifyIsNull(fieldName: String): Assertion =
    record.get(fieldName) shouldBe null

  private def getFieldSchema(name: String): Schema.Field =
    schema.getFields.asScala.find(_.name() == name).get

  private lazy val record =
    new LogicalTypesGenericRecordBuilder(schema).build()

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
       |    },
       |    {
       |      "name": "unionOfIntAndRecord_5",
       |      "type": ["int", { "type": "record", "name": "record5", "fields": [] }],
       |      "default": 42
       |    },
       |    {
       |      "name": "unionOfRecordAndInt_6",
       |      "type": [{ "type": "record", "name": "record6", "fields": [] }, "int"],
       |      "default": {}
       |    },
       |    {
       |      "name": "uuidField_7",
       |      "type": { "type": "string", "logicalType": "uuid" },
       |      "default": "00000000-0000-0000-0000-000000000000"
       |    },
       |    {
       |      "name": "timestampMillisField_8",
       |      "type": { "type": "long", "logicalType": "timestamp-millis" },
       |      "default": 0
       |    }
       |   ]
       |}
    """.stripMargin)
}
