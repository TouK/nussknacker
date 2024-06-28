package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.Schema
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.expression.Expression

class AvroDefaultExpressionDeterminerTest extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  test("string default") {
    verify("stringField_0")(
      { _ shouldBe Some("'stringDefault'".spel) },
    )
  }

  test("null default") {
    verifyIsNull("nullableStringField_1")
  }

  test("long default") {
    verify("longField_2")(
      { _ shouldBe Some("42L".spel) },
    )
  }

  test("not supported record default") {
    val recordField = getField("recordField_3")
    val expression  = new AvroDefaultExpressionDeterminer(handleNotSupported = false).determine(recordField)

    expression shouldBe Invalid(
      AvroDefaultExpressionDeterminer.TypeNotSupported(recordField.schema())
    ).toValidatedNel
  }

  test("not supported record default with not supported type handling") {
    val recordField         = getField("recordField_3")
    val validatedExpression = new AvroDefaultExpressionDeterminer(handleNotSupported = true).determine(recordField)
    validatedExpression shouldBe Valid(None)
  }

  test("nullable record with null default") {
    verifyIsNull("nullableRecord_4")
  }

  test("union with default of supported type") {
    verify("unionOfIntAndRecord_5")(
      { _ shouldBe Some("42".spel) },
    )
  }

  test("union with default of not supported type") {
    val unionOfRecordAndInt = getField("unionOfRecordAndInt_6")
    val expression = new AvroDefaultExpressionDeterminer(handleNotSupported = false).determine(unionOfRecordAndInt)

    expression shouldBe Invalid(
      AvroDefaultExpressionDeterminer.TypeNotSupported(unionOfRecordAndInt.schema())
    ).toValidatedNel
  }

  test("uuid default") {
    verify("uuidField_7")(
      { _ shouldBe Some("T(java.util.UUID).fromString('00000000-0000-0000-0000-000000000000')".spel) },
    )
  }

  private def verifyIsNull(fieldName: String): Unit = {
    verify(fieldName) {
      { _ shouldBe Some("null".spel) }
    }
  }

  private def verify(fieldName: String)(expressionAssertion: Option[Expression] => Assertion): Unit = {
    val field               = getField(fieldName)
    val validatedExpression = new AvroDefaultExpressionDeterminer(handleNotSupported = false).determine(field)
    val expression          = validatedExpression.valueOr(errors => throw errors.head)
    expressionAssertion(expression)
    expression.map(evaluate).foreach(_ shouldEqual record.get(fieldName))
  }

  private def evaluate(expression: Expression): AnyRef = {
    val parser = new org.springframework.expression.spel.standard.SpelExpressionParser
    parser.parseExpression(expression.expression).getValue()
  }

  private def getField(name: String): Schema.Field =
    schema.getFields.asScala.find(_.name() == name).get

  private lazy val record =
    new LogicalTypesGenericRecordBuilder(schema).build()

  private lazy val schema =
    AvroUtils.parseSchema(s"""
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
       |    }
       |   ]
       |}
    """.stripMargin)

}
