package pl.touk.nussknacker.engine.json.encode

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import io.circe.Json
import io.circe.Json.{Null, obj}
import org.everit.json.schema._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}

class BestEffortJsonSchemaEncoderTest extends AnyFunSuite {

  import collection.JavaConverters._

  private val FieldName = "foo"
  
  private val encoder = BestEffortJsonSchemaEncoder

  private val schemaNumber: NumberSchema = NumberSchema.builder().build()
  private val schemaString: StringSchema = StringSchema.builder().build()
  private val schemaNull: NullSchema = NullSchema.INSTANCE

  private val schemaObjString: ObjectSchema = createSchemaObjWithFooField(false, schemaString)
  private val schemaObjUnionNullString: ObjectSchema = createSchemaObjWithFooField(false, schemaNull, schemaString)
  private val schemaObjUnionNullStringRequired: ObjectSchema = createSchemaObjWithFooField(true, schemaNull, schemaString)

  test("should encode object") {
    val schema: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "firstName": {
        |      "type": "string"
        |    },
        |    "lastName": {
        |      "type": "string"
        |    },
        |    "age": {
        |      "type": "integer"
        |    }
        |  }
        |}""".stripMargin)

    val encoded = encoder.encode(Map(
      "firstName" -> "John",
      "lastName" -> "Smith",
      "age" -> 1L
    ), schema)

    encoded shouldEqual Valid(Json.obj(
      "firstName" -> Json.fromString("John"),
      "lastName" -> Json.fromString("Smith"),
      "age" -> Json.fromLong(1),
    ))

    encoder.encode(Map(
      "firstName" -> "John",
      "lastName" -> 1,
      "age" -> 1L
    ), schema) shouldBe Invalid(NonEmptyList.of("""Not expected type: java.lang.Integer for field: 'lastName' with schema: {"type":"string"}."""))
  }

  test("should encode string") {
    val encoded = encoder.encode("1", schemaString)
    encoded shouldEqual Valid(Json.fromString("1"))
  }

  test("should encode date time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date-time"}""".stripMargin)
    val zdt = ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    val encodedZdt = encoder.encode(zdt, schema)
    encodedZdt shouldEqual Valid(Json.fromString("2020-07-10T12:12:30+02:00"))

    val encodedOdt = encoder.encode(zdt.toOffsetDateTime, schema)
    encodedOdt shouldEqual Valid(Json.fromString("2020-07-10T12:12:30+02:00"))
  }

  test("should encode date") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date"}""".stripMargin)
    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    val encoded = encoder.encode(date, schema)

    encoded shouldEqual Valid(Json.fromString("2020-07-10"))
  }

  test("should encode time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "time"}""".stripMargin)
    val date = OffsetTime.parse("20:20:39+01:00", DateTimeFormatter.ISO_OFFSET_TIME)
    val encoded = encoder.encode(date, schema)

    encoded shouldEqual Valid(Json.fromString("20:20:39+01:00"))
  }

  test("should throw when wrong date-time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date-time"}""".stripMargin)
    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    val encoded = encoder.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should throw when wrong time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "time"}""".stripMargin)
    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    val encoded = encoder.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should throw when wrong date") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date"}""".stripMargin)
    val date = ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    val encoded = encoder.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should encode number") {
    val encoded = encoder.encode(1L, schemaNumber)
    encoded shouldEqual Valid(Json.fromLong(1L))
  }

  test("should throw when wrong number") {
    val schemaIntWithMinMax = NumberSchema.builder().requiresInteger(true).minimum(1).maximum(16).build()
    val objWithIntWithMinMax = createSchemaObjWithFooField(false, schemaIntWithMinMax)

    forAll(Table(
      ("data", "schema", "expected"),
      (0, schemaIntWithMinMax,  invalid(s"#: 0 is not greater or equal to 1")),
      (17, schemaIntWithMinMax, invalid(s"#: 17 is not less or equal to 16")),
      (Map(FieldName -> 0), objWithIntWithMinMax, invalid(s"#/$FieldName: 0 is not greater or equal to 1")),
      (Map(FieldName -> 17), objWithIntWithMinMax, invalid(s"#/$FieldName: 17 is not less or equal to 16")),
    )) { (data, schema, expected) =>
      encoder.encodeWithJsonValidation(data, schema) shouldBe expected
    }
  }

  test("should encode array") {
    val schema: ArraySchema = ArraySchema.builder().allItemSchema(schemaNumber).build()
    val encoded = encoder.encode(List(1), schema)

    encoded shouldEqual Valid(Json.arr(Json.fromLong(1L)))
  }

  test("should throw when value and schema type mismatch") {
    forAll(Table(
      ("data", "schema", "expected"),
      ("1", schemaNumber, invalid(s"Not expected type: java.lang.String for field with schema: $schemaNumber.")),
      (Map(FieldName -> null), createSchemaObjWithFooField(true, schemaString), invalid(s"Not expected type: null for field: '$FieldName' with schema: $schemaString.")),
      (Map(FieldName -> null), schemaObjString, invalid(s"Not expected type: null for field: 'foo' with schema: $schemaString.")),
    )) { (data, schema, expected) =>
      encoder.encode(data, schema) shouldBe expected
    }
  }

  test("should accept and encode redundant parameters if schema allows this") {
    val rejectAdditionalProperties: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": false
        |}""".stripMargin)

    encoder.encode(Map("foo" -> "bar", "redundant" -> 15), schemaObjString) shouldBe Valid(Json.obj(("foo", Json.fromString("bar")), ("redundant", Json.fromLong(15))))
    encoder.encode(Map("foo" -> "bar", "redundant" -> 15), rejectAdditionalProperties) shouldBe 'invalid
  }

  test("should validate additionalParameters type") {
    def schema(additionalPropertiesType: String): Schema = JsonSchemaBuilder.parseSchema(
      s"""{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": {
        |     "type": "$additionalPropertiesType"
        |  }
        |}""".stripMargin)

    encoder.encode(Map("foo" -> "bar", "redundant" -> "aaa"), schema("number")) shouldBe
      Invalid(NonEmptyList.of("""Not expected type: java.lang.String for field with schema: {"type":"number"}."""))
    encoder.encode(Map("foo" -> "bar", "redundant" -> 15), schema("number")) shouldBe
      Valid(Json.obj(("foo", Json.fromString("bar")), ("redundant", Json.fromLong(15))))
    encoder.encode(Map("foo" -> "bar", "redundant" -> 15), schema("string")) shouldBe 'invalid
  }

  test("should encode union") {
    forAll(Table(
      "schema",
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["string", "integer"]
        |    }
        |  }
        |}""".stripMargin,
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "anyOf": [
        |        { "type": "string" },
        |        { "type": "integer" }
        |      ]
        |    }
        |  }
        |}""".stripMargin,
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "oneOf": [
        |        { "type": "string" },
        |        { "type": "integer" }
        |      ]
        |    }
        |  }
        |}""".stripMargin
    )) { schemaString =>
      val schema: Schema = JsonSchemaBuilder.parseSchema(schemaString)
      encoder.encode(Map("foo" -> 1), schema) shouldBe Valid(Json.obj(("foo", Json.fromLong(1L))))
      encoder.encode(Map("foo" -> "1"), schema) shouldBe Valid(Json.obj(("foo", Json.fromString("1"))))
    }
  }

  test("handling encode null value") {
    forAll(Table(
      ("data", "schema", "result"),
      (Map(), schemaObjString, obj()),
      (Map(), schemaObjUnionNullString, obj()),
      (Map(FieldName-> null), schemaObjUnionNullString, obj("foo" -> Null)),
      (Map(FieldName -> null), schemaObjUnionNullStringRequired, obj("foo" -> Null)),
    )) { (data, schema, result) =>
      encoder.encode(data, schema) shouldBe Valid(result)
    }
  }

  test("should reject when missing required field") {
    val objWithReqAndDefault = StringSchema.builder().defaultValue("def").build()

    forAll(Table(
      ("data", "schema"),
      (Map(), createSchemaObjWithFooField(true, schemaString)),
      (Map(), createSchemaObjWithFooField(true, objWithReqAndDefault)), // Everit throws exception for required field even if schema has default value
      (Map(), schemaObjUnionNullStringRequired),
    )) { (data, schema) =>
      val expected = invalid(s"Missing property: $FieldName for schema: $schema.")
      encoder.encode(data, schema) shouldBe expected
    }
  }

  private def createSchemaObjWithFooField(required: Boolean, schemas: Schema*): ObjectSchema = {
    val schema = schemas.toList match {
      case head :: Nil => head
      case list => CombinedSchema.anyOf(list.asJava).build()
    }

    val builder = ObjectSchema.builder()
    builder.addPropertySchema(FieldName, schema)

    if (required) {
      builder.addRequiredProperty(FieldName)
    }

    builder.build()
  }

  private def invalid(msg: String*): Invalid[NonEmptyList[String]] =
    Invalid(NonEmptyList.fromListUnsafe(msg.toList))
}
