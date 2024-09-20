package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.GenericRecord
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedDict}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.schemedkafka.schema.PaymentV1
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Standard
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder

import java.time.{Instant, LocalDate, LocalTime}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class AvroSchemaSpelExpressionSpec extends AnyFunSpec with Matchers {

  private implicit val nid: NodeId = NodeId("")

  private val dictId = "dict1"

  it("should recognize record with simple fields") {
    val schema = wrapWithRecordSchema("""[
        |  { "name": "intField", "type": "int" },
        |  { "name": "stringField", "type": "string" },
        |  { "name": "booleanField", "type": "boolean" }
        |]""".stripMargin)
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    parse[Integer]("#input.intField", ctx) should be(Symbol("valid"))
    parse[CharSequence]("#input.intField", ctx) should be(Symbol("invalid"))
    parse[CharSequence]("#input.stringField", ctx) should be(Symbol("valid"))
    parse[Boolean]("#input.booleanField", ctx) should be(Symbol("valid"))
    parse[Integer]("#input.nonExisting", ctx) should be(Symbol("invalid"))
    parse[GenericRecord]("#input", ctx) should be(Symbol("valid"))
  }

  it("should recognize record with list field") {
    val schema = wrapWithRecordSchema("""[
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
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    parse[CharSequence]("#input.array[0].foo", ctx) should be(Symbol("valid"))
    parse[CharSequence]("#input.array[0].bar", ctx) should be(Symbol("invalid"))
  }

  it("should recognize record with map field") {
    val schema = wrapWithRecordSchema("""[
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
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    parse[CharSequence]("#input.map['ala'].foo", ctx) should be(Symbol("valid"))
    parse[CharSequence]("#input.map['ala'].bar", ctx) should be(Symbol("invalid"))
  }

  it("should recognize record with simple union") {
    val schema = wrapWithRecordSchema("""[
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
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    parse[CharSequence]("#input.union.foo", ctx) should be(Symbol("valid"))
    parse[CharSequence]("#input.union.bar", ctx) should be(Symbol("invalid"))
  }

  it("should recognize record with class union") {
    val schema = wrapWithRecordSchema("""[
        |  {
        |    "name": "union",
        |    "type": [
        |      "null",
        |      "int",
        |      "long"
        |    ]
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    parse[Int]("#input.union", ctx) should be(Symbol("valid"))
    parse[Long]("#input.union", ctx) should be(Symbol("valid"))
    parse[CharSequence]("#input.union", ctx) should be(Symbol("invalid"))
  }

  it("should recognize record with enum") {
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(PaymentV1.schema), paramName = None)
      .toOption
      .get
    parse[CharSequence]("#input.currency.toString", ctx) should be(Symbol("valid"))
    parse[EnumSymbol]("#input.currency", ctx) should be(Symbol("valid"))
  }

  it("should not skipp nullable field vat from schema PaymentV1 when skippNullableFields is set") {
    val typeResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(PaymentV1.schema)
    val ctx        = ValidationContext.empty.withVariable("input", typeResult, paramName = None).toOption.get

    parse[Int]("#input.vat", ctx) should be(Symbol("valid"))
  }

  it("should add dictionaryId if annotated") {
    val schema = wrapWithRecordSchema(s"""[
        |  {
        |    "name": "withDict",
        |    "type": "string",
        |    "${AvroSchemaTypeDefinitionExtractor.dictIdProperty}": "$dictId"
        |  }
        |]""".stripMargin)
    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .andThen(_.withVariable("DICT1", TypedDict(dictId, Typed.typedClass[String]), paramName = None))
      .toOption
      .get

    parse[CharSequence]("#input.withDict", ctx) should be(Symbol("valid"))
    parse[Boolean]("#input.withDict == #DICT1['key1']", ctx) should be(Symbol("valid"))
    parse[Boolean]("#input.withDict == #DICT1['noKey']", ctx) should be(Symbol("invalid"))
  }

  it("should recognize avro type string as String") {
    val schema = wrapWithRecordSchema("""[
        |  { "name": "stringField", "type": "string" },
        |  { "name": "mapField", "type": { "type": "map", "values": "string" } }
        |]""".stripMargin)

    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    parse[String]("#input.stringField", ctx) should be(Symbol("valid"))
    parse[AnyRef]("#input.mapField", ctx).map(_.returnType) shouldBe Valid(
      Typed.fromDetailedType[java.util.Map[String, String]]
    )

  }

  it("should recognize date types") {
    val schema = wrapWithRecordSchema("""[
        |  { "name": "date", "type": { "type": "int", "logicalType": "date" } },
        |  { "name": "timeMillis", "type": { "type": "int", "logicalType": "time-millis" } },
        |  { "name": "timeMicros", "type": { "type": "int", "logicalType": "time-micros" } },
        |  { "name": "localTimestampMillis", "type": { "type": "long", "logicalType": "local-timestamp-millis" } },
        |  { "name": "localTimestampMicros", "type": { "type": "long", "logicalType": "local-timestamp-micros" } },
        |  { "name": "timestampMillis", "type": { "type": "long", "logicalType": "timestamp-millis" } },
        |  { "name": "timestampMicros", "type": { "type": "long", "logicalType": "timestamp-micros" } }
        |]""".stripMargin)

    val ctx = ValidationContext.empty
      .withVariable("input", AvroSchemaTypeDefinitionExtractor.typeDefinition(schema), paramName = None)
      .toOption
      .get

    def checkTypeForExpr[Type: ClassTag](expr: String) = {
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

  private def parse[T: TypeTag](
      expr: String,
      validationCtx: ValidationContext
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    SpelExpressionParser
      .default(
        getClass.getClassLoader,
        ModelDefinitionBuilder.emptyExpressionConfig,
        new SimpleDictRegistry(Map(dictId -> EmbeddedDictDefinition(Map("key1" -> "value1")))),
        enableSpelForceCompile = true,
        Standard,
        ClassDefinitionTestUtils.createDefinitionForClasses(classOf[EnumSymbol]),
        ClassDefinitionTestUtils.DefaultSettings
      )
      .parse(expr, validationCtx, Typed.fromDetailedType[T])
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(s"""{
                                 |  "name": "sample",
                                 |  "type": "record",
                                 |  "fields": $fieldsDefinition
                                 |}""".stripMargin)

}
