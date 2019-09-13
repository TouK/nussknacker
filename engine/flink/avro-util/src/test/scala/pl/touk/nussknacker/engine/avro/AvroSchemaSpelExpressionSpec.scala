package pl.touk.nussknacker.engine.avro

import cats.data.ValidatedNel
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, TypedExpression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Standard

import scala.concurrent.duration._
import scala.reflect.runtime.universe._

class AvroSchemaSpelExpressionSpec extends FunSpec with Matchers {

  implicit val nid: NodeId = NodeId("")

  it("should recognize record with simple fields") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "intField", "type": "int" },
        |  { "name": "stringField", "type": "string" },
        |  { "name": "booleanField", "type": "boolean" }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)).toOption.get

    parse[Integer]("#input.intField", ctx) should be ('valid)
    parse[CharSequence]("#input.intField", ctx) should be ('invalid)
    parse[CharSequence]("#input.stringField", ctx) should be ('valid)
    parse[Boolean]("#input.booleanField", ctx) should be ('valid)
    parse[Integer]("#input.nonExisting", ctx) should be ('invalid)
    parse[GenericRecord]("#input", ctx) should be ('valid)
  }

  it("should recognize record with list field") {
    val schema = wrapWithRecordSchema(
      """[
        |  {
        |    "name": "array",
        |    "type": {
        |      "type": "array",
        |      "items": {
        |        "name": "element",
        |        "type": "record",
        |        "fields": [
        |          { "name": "foo", "type": "string" }
        |        ]
        |      }
        |    }
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)).toOption.get

    parse[CharSequence]("#input.array[0].foo", ctx) should be ('valid)
    parse[CharSequence]("#input.array[0].bar", ctx) should be ('invalid)
  }

  it("should recognize record with map field") {
    val schema = wrapWithRecordSchema(
      """[
        |  {
        |    "name": "map",
        |    "type": {
        |      "type": "map",
        |      "values": {
        |        "name": "element",
        |        "type": "record",
        |        "fields": [
        |          { "name": "foo", "type": "string" }
        |        ]
        |      }
        |    }
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)).toOption.get

    parse[CharSequence]("#input.map['ala'].foo", ctx) should be ('valid)
    parse[CharSequence]("#input.map['ala'].bar", ctx) should be ('invalid)
  }

  it("should recognize record with simple union") {
    val schema = wrapWithRecordSchema(
      """[
        |  {
        |    "name": "union",
        |    "type": [
        |      "null",
        |      {
        |        "name": "element",
        |        "type": "record",
        |        "fields": [
        |          { "name": "foo", "type": "string" }
        |        ]
        |      }
        |    ]
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)).toOption.get

    parse[CharSequence]("#input.union.foo", ctx) should be ('valid)
    parse[CharSequence]("#input.union.bar", ctx) should be ('invalid)
  }

  it("should recognize record with class union") {
    val schema = wrapWithRecordSchema(
      """[
        |  {
        |    "name": "union",
        |    "type": [
        |      "null",
        |      "int",
        |      "long"
        |    ]
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)).toOption.get

    parse[Int]("#input.union", ctx) should be ('valid)
    parse[Long]("#input.union", ctx) should be ('valid)
    parse[CharSequence]("#input.union", ctx) should be ('invalid)
  }

  private def parse[T:TypeTag](expr: String, validationCtx: ValidationContext) : ValidatedNel[ExpressionParseError, TypedExpression] = {
    SpelExpressionParser.default(getClass.getClassLoader, enableSpelForceCompile = true, Nil, Standard).parse(expr, validationCtx, Typed[T])
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(s"""{
                                 |  "name": "sample",
                                 |  "type": "record",
                                 |  "fields": $fieldsDefinition
                                 |}""".stripMargin)


}
