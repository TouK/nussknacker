package pl.touk.nussknacker.engine.lite.components.utils

import io.circe.Json
import io.circe.Json._
import org.everit.json.schema.{ArraySchema, CombinedSchema, NullSchema, NumberSchema, ObjectSchema, Schema, StringSchema}
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.test.SpecialSpELElement

import java.time.Year

object JsonTestData {

  import collection.JavaConverters._

  val ObjectFieldName: String = "field"

  val OutputField: SpecialSpELElement = SpecialSpELElement(s"#input.$ObjectFieldName")

  val schemaPerson: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "first": {
      |      "type": "string"
      |    },
      |    "last": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "type": "integer"
      |    }
      |  },
      |  "additionalProperties": false
      |}""".stripMargin)


  val schemaNull: Schema = NullSchema.INSTANCE

  val schemaString: Schema = StringSchema.builder().build()

  val schemaInteger: Schema = NumberSchema.builder().requiresInteger(true).build()

  val schemaUnionStrInt: Schema = asSchema(schemaString, schemaInteger)

  val schemaIntegerRange: Schema = NumberSchema.builder()
    .requiresInteger(true)
    .minimum(Integer.MIN_VALUE)
    .maximum(Integer.MAX_VALUE)
    .build()

  val nameAndLastNameSchema: Schema = nameAndLastNameSchema(true)

  def nameAndLastNameSchema(additionalProperties: Any): Schema = JsonSchemaBuilder.parseSchema(
    s"""{
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

  val schemaListIntegers: ArraySchema = JsonSchemaBuilder.parseSchema(s"""{"type": "array", "items": $schemaInteger}""".stripMargin)

  val schemaMapAny: ObjectSchema = createMapSchema()

  val schemaMapString: ObjectSchema = createMapSchema(schemaString)

  val schemaMapInteger: ObjectSchema = createMapSchema(schemaInteger)

  val schemaMapStringOrInt: ObjectSchema = createMapSchema(schemaString, schemaInteger)

  val schemaMapObjPerson: ObjectSchema = createMapSchema(schemaPerson)

  val schemaObjMapAny: ObjectSchema = createObjSchema(schemaMapAny)

  val schemaObjMapInteger: ObjectSchema = createObjSchema(schemaMapInteger)

  val schemaObjInteger: ObjectSchema = createObjSchema(schemaInteger)

  val schemaObjString: ObjectSchema = createObjSchema(schemaString)

  val schemaObjNull: ObjectSchema = createObjSchema(schemaNull)

  val schemaObjUnionNullString: ObjectSchema = createObjSchema(schemaNull, schemaString)

  val schemaObjMapObjPerson: ObjectSchema = createObjSchema(schemaMapObjPerson)


  /* SpEL sink output configuration */
  val sampleInt = 13

  val sampleStr = "json-str"

  val year: Int = Year.now.getValue

  val strNu: String = "Nu"

  val strTouK: String = "TouK"

  val samplePersonSpEL: Map[String, Any] = Map("first" -> strNu, "last" -> strTouK, "age" -> sampleInt)

  val sampleMapAnySpEL: Map[String, Any] = Map("first" -> strNu, "year" -> year)

  val sampleMapIntSpEL: Map[String, Integer] = Map("year" -> year)

  val sampleMapPersonSpEL: Map[String, Map[String, Any]] = Map("first" -> samplePersonSpEL)

  val objOutputAsInputFieldSpEL: Map[String, SpecialSpELElement] = Map(ObjectFieldName -> OutputField)

  val sampleObjStrSpEL: Map[String, String] = Map(ObjectFieldName -> sampleStr)

  val sampleObjMapIntSpEL: Map[String, Map[String, Integer]] = Map(ObjectFieldName -> sampleMapIntSpEL)

  val sampleObjMapAnySpEL: Map[String, Map[String, Any]] = Map(ObjectFieldName -> sampleMapAnySpEL)

  val sampleObjMapPersonSpEL: Map[String, Map[String, Any]] = Map(ObjectFieldName -> sampleMapPersonSpEL)


  /* Input / Output json configuration */

  val sampleJInt: Json = fromInt(sampleInt)

  val sampleJStr: Json = fromString(sampleStr)

  val samplePerson: Json = obj("first" -> fromString(strNu), "last" -> fromString(strTouK), "age" -> sampleJInt)

  val sampleMapAny: Json = obj("first" -> fromString(strNu), "year" -> fromInt(year))

  val sampleMapInt: Json = obj("year" -> fromInt(year))

  val sampleMapStr: Json = obj("foo" -> fromString("bar"))

  val sampleObjNull: Json = JsonObj(Json.Null)

  val sampleObjStr: Json = JsonObj(sampleJStr)

  val sampleObjMapAny: Json = JsonObj(sampleMapAny)

  val sampleObjMapInt: Json = JsonObj(sampleMapInt)

  val sampleObjMapPerson: Json = JsonObj(obj("first" -> samplePerson))

  object JsonObj {
    def apply(value: Json): Json = obj(ObjectFieldName -> value)
  }

  //Empty list of schema means map of any type
  def createMapSchema(schemas: Schema*): ObjectSchema = {
    val builder = ObjectSchema.builder()

    val additionalProperties = schemas match {
      case Nil => true
      case list => asSchema(list: _*)
    }

    prepareAdditionalProperties(builder, additionalProperties)

    builder.build()
  }

  def createObjSchema(schemas: Schema*): ObjectSchema = createObjSchema(false, false, schemas: _*)

  def createObjSchema(additionalProperties: Any, required: Boolean, schemas: Schema*): ObjectSchema = {
    val schema = asSchema(schemas: _*)
    val builder = ObjectSchema.builder()
    builder.addPropertySchema(ObjectFieldName, schema)

    if (required) {
      builder.addRequiredProperty(ObjectFieldName)
    }

    prepareAdditionalProperties(builder, additionalProperties)

    builder.build()
  }

  //We assume list of schema is union with combined mode
  private def asSchema(schemas: Schema*): Schema = schemas.toList match {
    case head :: Nil => head
    case list => CombinedSchema.anyOf(list.asJava).build()
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
}
