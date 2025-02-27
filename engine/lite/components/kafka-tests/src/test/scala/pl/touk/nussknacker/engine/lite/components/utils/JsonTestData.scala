package pl.touk.nussknacker.engine.lite.components.utils

import io.circe.Json
import io.circe.Json._
import org.everit.json.schema.{
  ArraySchema,
  CombinedSchema,
  NullSchema,
  NumberSchema,
  ObjectSchema,
  Schema,
  StringSchema
}
import org.everit.json.schema.regexp.JavaUtilRegexpFactory
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.test.SpecialSpELElement

import java.lang.Long
import java.time.Year

object JsonTestData {

  import scala.jdk.CollectionConverters._

  val ObjectFieldName: String = "field"

  val OutputField: SpecialSpELElement = SpecialSpELElement(s"#input.$ObjectFieldName")

  val schemaPerson: Schema = schemaPerson(None, None)

  val schemaPersonWithLimits: Schema = schemaPerson(Some(10), Some(200))

  val schemaPersonWithUpperLimits: Schema = schemaPerson(None, Some(201), exclusiveMaximum = true)

  val schemaPersonWithLowerLimits: Schema = schemaPerson(Some(300), None, exclusiveMinimum = true)

  val schemaTrue: Schema = JsonSchemaBuilder.parseSchema("true")

  val schemaEmpty: Schema = JsonSchemaBuilder.parseSchema("{}")

  val schemaNull: Schema = NullSchema.INSTANCE

  val schemaString: Schema = StringSchema.builder().build()

  val schemaInt: Schema = NumberSchema
    .builder()
    .requiresInteger(true)
    .minimum(Integer.MIN_VALUE)
    .maximum(Integer.MAX_VALUE)
    .build()

  val schemaIntRange0to100: Schema = NumberSchema
    .builder()
    .requiresInteger(true)
    .minimum(0)
    .maximum(100)
    .build()

  val schemaIntRangeTo100: Schema = NumberSchema
    .builder()
    .requiresInteger(true)
    .maximum(100)
    .build()

  val schemaLong: Schema = NumberSchema.builder().requiresInteger(true).build()

  val schemaBigDecimal: Schema = NumberSchema.builder().build()

  val nameAndLastNameSchema: Schema = nameAndLastNameSchema(true)

  def nameAndLastNameSchema(additionalProperties: Any): Schema = JsonSchemaBuilder.parseSchema(s"""{
       |  "type": "object",
       |  "properties": {
       |    "first": {
       |      "type": "string"
       |    },
       |    "last": {
       |      "type": "string"
       |    }
       |  },
       |  "additionalProperties": $additionalProperties
       |}""".stripMargin)

  val schemaArrayLong: Schema = createArraySchema(schemaLong)

  val schemaMapAny: ObjectSchema = createMapSchema()

  val schemaMapStr: ObjectSchema = createMapSchema(schemaString)

  val schemaMapLong: ObjectSchema = createMapSchema(schemaLong)

  val schemaMapStringOrLong: ObjectSchema = createMapSchema(schemaString, schemaLong)

  val schemaMapObjPerson: ObjectSchema = createMapSchema(schemaPerson)

  val schemaMapObjPersonWithLimits: ObjectSchema = createMapSchema(schemaPersonWithLimits)

  val schemaObjMapAny: ObjectSchema = createObjSchema(schemaMapAny)

  val schemaObjMapLong: ObjectSchema = createObjSchema(schemaMapLong)

  val schemaObjLong: ObjectSchema = createObjSchema(schemaLong)

  val schemaObjStr: ObjectSchema = createObjSchema(schemaString)

  val schemaObjNull: ObjectSchema = createObjSchema(schemaNull)

  val schemaObjUnionNullStr: ObjectSchema = createObjSchema(schemaNull, schemaString)

  val schemaObjMapObjPerson: ObjectSchema = createObjSchema(schemaMapObjPerson)

  val schemaEnumAB: Schema  = JsonSchemaBuilder.parseSchema("""{ "enum": ["A", "B" ] }""".stripMargin)
  val schemaEnumABC: Schema = JsonSchemaBuilder.parseSchema("""{ "enum": ["A", "B", "C"] }""".stripMargin)
  val schemaEnumAB1: Schema = JsonSchemaBuilder.parseSchema("""{ "enum": ["A", "B", 1] }""".stripMargin)
  val schemaEnumStrOrObj: Schema =
    JsonSchemaBuilder.parseSchema("""{ "enum": ["A", {"x":"A", "y": [1,2]}] }""".stripMargin)
  val schemaEnumStrOrList: Schema = JsonSchemaBuilder.parseSchema("""{ "enum": ["A", [1,2,3] ] }""".stripMargin)

  /* SpEL sink output configuration */
  val sampleInt = 13

  val sampleLong = Integer.MAX_VALUE.toLong + 1

  val sampleStr = "json-str"

  val year: Int = Year.now.getValue

  val strNu: String = "Nu"

  val strTouK: String = "TouK"

  val samplePersonOutput: Map[String, Any] = Map("first" -> strNu, "last" -> strTouK, "age" -> sampleInt)

  val sampleMapAnyOutput: Map[String, Any] = Map("first" -> strNu, "year" -> year)

  val sampleMapIntOutput: Map[String, Integer] = Map("year" -> year)

  val sampleMapPersonOutput: Map[String, Map[String, Any]] = Map("first" -> samplePersonOutput)

  val objOutputAsInputField: Map[String, SpecialSpELElement] = Map(ObjectFieldName -> OutputField)

  val sampleObjStrOutput: Map[String, String] = Map(ObjectFieldName -> sampleStr)

  val sampleObjMapIntOutput: Map[String, Map[String, Integer]] = Map(ObjectFieldName -> sampleMapIntOutput)

  val sampleObjMapAnyOutput: Map[String, Map[String, Any]] = Map(ObjectFieldName -> sampleMapAnyOutput)

  val sampleObjMapPersonOutput: Map[String, Map[String, Any]] = Map(ObjectFieldName -> sampleMapPersonOutput)

  /* Input / Output json configuration */

  val sampleJInt: Json = fromInt(sampleInt)

  val sampleJLongFromInt: Json = fromLong(sampleInt)

  val sampleJBigDecimalFromInt: Json = fromBigDecimal(sampleInt)

  val sampleJLong: Json = fromLong(sampleLong)

  val sampleJBigDecimalFromLong: Json = fromBigDecimal(sampleLong)

  val sampleJMinInt: Json = fromInt(Int.MinValue)

  val sampleJStr: Json = fromString(sampleStr)

  val samplePerson: Json = obj("first" -> fromString(strNu), "last" -> fromString(strTouK), "age" -> sampleJInt)

  val sampleInvalidPerson: Json =
    obj("first" -> fromString(strNu), "last" -> fromString(strTouK), "age" -> fromInt(300))

  val sampleArrayInt: Json = arr(sampleJInt)

  val sampleMapAny: Json = obj("first" -> fromString(strNu), "year" -> fromInt(year))

  val sampleMapInt: Json = obj("year" -> fromInt(year))

  val sampleMapStr: Json = obj("foo" -> fromString("bar"))

  val sampleMapPerson: Json = obj("first" -> samplePerson)

  val sampleObjNull: Json = JsonObj(Json.Null)

  val sampleObjStr: Json = JsonObj(sampleJStr)

  val sampleObjMapAny: Json = JsonObj(sampleMapAny)

  val sampleObjMapInt: Json = JsonObj(sampleMapInt)

  val sampleObjMapPerson: Json = JsonObj(sampleMapPerson)

  object JsonObj {
    def apply(value: Json): Json = obj(ObjectFieldName -> value)
  }

  // Empty list of schema means map of any type
  def createMapSchema(schemas: Schema*): ObjectSchema = {
    val builder = ObjectSchema.builder()

    val additionalProperties = schemas match {
      case Nil  => true
      case list => asSchema(list: _*)
    }

    prepareAdditionalProperties(builder, additionalProperties)

    builder.build()
  }

  def createObjSchema(schemas: Schema*): ObjectSchema = createObjSchema(false, false, schemas: _*)

  def createObjSchema(required: Boolean, schemas: Schema*): ObjectSchema = {
    createObjSchema(false, required, schemas: _*)
  }

  def createObjSchema(additionalProperties: Any, required: Boolean, schemas: Schema*): ObjectSchema = {
    val schema  = asSchema(schemas: _*)
    val builder = ObjectSchema.builder()
    builder.addPropertySchema(ObjectFieldName, schema)

    if (required) {
      builder.addRequiredProperty(ObjectFieldName)
    }

    prepareAdditionalProperties(builder, additionalProperties)

    builder.build()
  }

  def createArraySchema(schemas: Schema*): ArraySchema =
    ArraySchema.builder().allItemSchema(asSchema(schemas: _*)).build()

  // We assume list of schema is union with combined mode
  private def asSchema(schemas: Schema*): Schema = schemas.toList match {
    case head :: Nil => head
    case list        => CombinedSchema.anyOf(list.asJava).build()
  }

  private def prepareAdditionalProperties(builder: ObjectSchema.Builder, additionalProperties: Any): Unit = {
    additionalProperties match {
      case bool: Boolean =>
        builder.additionalProperties(bool)
      case sch: Schema =>
        builder.additionalProperties(true)
        builder.schemaOfAdditionalProperties(sch)
      case _ =>
        throw new IllegalArgumentException(s"Unknown `additionalProperties` value: $additionalProperties.")
    }
  }

  private def schemaPerson(
      minimum: Option[Int],
      maximum: Option[Int],
      exclusiveMinimum: Boolean = false,
      exclusiveMaximum: Boolean = false
  ): Schema =
    JsonSchemaBuilder.parseSchema(s"""{
         |  "type": "object",
         |  "properties": {
         |    "first": {
         |      "type": "string"
         |    },
         |    "last": {
         |      "type": "string"
         |    },
         |    "age": {
         |      "type": "integer",
         |      ${minimum.fold("")(x => s""" "${if (exclusiveMinimum) "exclusiveMinimum" else "minimum"}": $x,""")}
         |      ${maximum.fold("")(x => s""" ${if (exclusiveMaximum) "exclusiveMaximum" else "maximum"}: $x""")}
         |    }
         |  },
         |  "additionalProperties": false
         |}""".stripMargin)

  def createObjectSchemaWithPatternProperties(
      patternProperties: Map[String, Schema],
      additionalPropertySchema: Option[Schema] = None,
      definedProperties: Map[String, Schema] = Map.empty
  ): Schema = {
    val builder = ObjectSchema.builder()
    definedProperties.foreach { case (propertyName, schema) =>
      builder.addPropertySchema(propertyName, schema)
    }
    val regexpFactory = new JavaUtilRegexpFactory
    patternProperties.foreach { case (pattern, schema) =>
      builder.patternProperty(regexpFactory.createHandler(pattern), schema)
    }
    additionalPropertySchema.foreach { additionalPropertySchema =>
      builder.schemaOfAdditionalProperties(additionalPropertySchema)
    }
    builder.build()
  }

}
