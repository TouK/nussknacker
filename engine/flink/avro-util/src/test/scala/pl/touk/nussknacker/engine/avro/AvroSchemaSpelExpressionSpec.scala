package pl.touk.nussknacker.engine.avro

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.GenericRecord
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, TypedExpression}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedDict}
import pl.touk.nussknacker.engine.avro.schema.{PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Standard

import java.time.{Instant, LocalDate, LocalTime, ZonedDateTime}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class AvroSchemaSpelExpressionSpec extends FunSpec with Matchers {

  private implicit val nid: NodeId = NodeId("")

  private val dictId = "dict1"

  it("should recognize record with simple fields") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "intField", "type": "int" },
        |  { "name": "stringField", "type": "string" },
        |  { "name": "booleanField", "type": "boolean" }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

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
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

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
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

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
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

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
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

    parse[Int]("#input.union", ctx) should be ('valid)
    parse[Long]("#input.union", ctx) should be ('valid)
    parse[CharSequence]("#input.union", ctx) should be ('invalid)
  }

  it("should recognize record with enum") {
    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(PaymentV1.schema), paramName = None).toOption.get

    parse[CharSequence]("#input.currency", ctx) should be ('valid)
    parse[EnumSymbol]("#input.currency", ctx) should be ('valid)
  }

  it("should not skipp nullable field vat from schema PaymentV1 when skippNullableFields is set") {
    val typeResult = AvroSchemaTypeDefinitionExtractor.typeDefinitionWithoutNullableFields(PaymentV1.schema, AvroSchemaTypeDefinitionExtractor.DefaultPossibleTypes)
    val ctx = ValidationContext.empty.withVariable("input", typeResult, paramName = None).toOption.get

    parse[Int]("#input.vat", ctx) should be ('valid)
  }

  it("should skipp optional fields from schema PaymentV2 when skippNullableFields is set") {
    val typeResult = AvroSchemaTypeDefinitionExtractor.typeDefinitionWithoutNullableFields(PaymentV2.schema, AvroSchemaTypeDefinitionExtractor.DefaultPossibleTypes)
    val ctx = ValidationContext.empty.withVariable("input", typeResult, paramName = None).toOption.get

    parse[Int]("#input.cnt", ctx) should be ('invalid)
    parse[Map[String, Any]]("#input.attributes", ctx) should be ('invalid)
  }

  it("should add dictionaryId if annotated") {
    val schema = wrapWithRecordSchema(
      s"""[
        |  {
        |    "name": "withDict",
        |    "type": "string",
        |    "${AvroSchemaTypeDefinitionExtractor.dictIdProperty}": "$dictId"
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty.withVariable("input",
      AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).andThen(_.withVariable("DICT1", TypedDict(dictId, Typed.typedClass[String]), paramName = None)).toOption.get

    parse[CharSequence]("#input.withDict", ctx) should be ('valid)
    parse[Boolean]("#input.withDict == #DICT1['key1']", ctx) should be ('valid)
    parse[Boolean]("#input.withDict == #DICT1['noKey']", ctx) should be ('invalid)
  }

  it("should recognize avro type string as String") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "stringField", "type": "string" },
        |  { "name": "mapField", "type": { "type": "map", "values": "string" } },
        |  { "name": "enumField", "type": { "type": "enum", "name": "sampleEnum", "symbols": ["One", "Two"] } }
        |]""".stripMargin)

    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

    parse[String]("#input.stringField", ctx) should be('valid)
    parse[String]("#input.enumField", ctx) should be('valid)
    parse[AnyRef]("#input.mapField", ctx).map(_.returnType) shouldBe Valid(Typed.fromDetailedType[java.util.Map[String, String]])

  }

  it("should recognize date types") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "date", "type": { "type": "int", "logicalType": "date" } },
        |  { "name": "timeMillis", "type": { "type": "int", "logicalType": "time-millis" } },
        |  { "name": "timeMicros", "type": { "type": "int", "logicalType": "time-micros" } },
        |  { "name": "localTimestampMillis", "type": { "type": "long", "logicalType": "local-timestamp-millis" } },
        |  { "name": "localTimestampMicros", "type": { "type": "long", "logicalType": "local-timestamp-micros" } },
        |  { "name": "timestampMillis", "type": { "type": "long", "logicalType": "timestamp-millis" } },
        |  { "name": "timestampMicros", "type": { "type": "long", "logicalType": "timestamp-micros" } }
        |]""".stripMargin)

    val ctx = ValidationContext.empty.withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None).toOption.get

    def checkTypeForExpr[Type:ClassTag](expr: String) = {
      parse[AnyRef](expr, ctx).map(_.returnType) shouldBe Valid(Typed[Type])
    }

    checkTypeForExpr[LocalDate]("#input.date")
    checkTypeForExpr[LocalTime]("#input.timeMillis")
    checkTypeForExpr[Int]("#input.timeMicros")
    checkTypeForExpr[Long]("#input.localTimestampMillis")
    checkTypeForExpr[Long]("#input.localTimestampMicros")
    checkTypeForExpr[Instant]("#input.timestampMillis")
    checkTypeForExpr[Instant]("#input.timestampMicros")
  }

  private def parse[T:TypeTag](expr: String, validationCtx: ValidationContext) : ValidatedNel[ExpressionParseError, TypedExpression] = {
    SpelExpressionParser.default(getClass.getClassLoader, new SimpleDictRegistry(Map(dictId -> EmbeddedDictDefinition(Map("key1" -> "value1")))), enableSpelForceCompile = true,
      strictTypeChecking = true, Nil, Standard, strictMethodsChecking = true, staticMethodInvocationsChecking = false, TypeDefinitionSet.empty, disableMethodExecutionForUnknown = false)(ClassExtractionSettings.Default).parse(expr, validationCtx, Typed.fromDetailedType[T])
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(s"""{
                                 |  "name": "sample",
                                 |  "type": "record",
                                 |  "fields": $fieldsDefinition
                                 |}""".stripMargin)


}
