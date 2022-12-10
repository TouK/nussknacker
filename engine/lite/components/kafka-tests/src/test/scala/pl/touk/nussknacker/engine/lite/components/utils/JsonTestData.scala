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

  val schemaArrayInt: Schema = createArraySchema(schemaInteger)

  val schemaMapAny: ObjectSchema = createMapSchema()

  val schemaMapStr: ObjectSchema = createMapSchema(schemaString)

  val schemaMapInt: ObjectSchema = createMapSchema(schemaInteger)

  val schemaMapStringOrInt: ObjectSchema = createMapSchema(schemaString, schemaInteger)

  val schemaMapObjPerson: ObjectSchema = createMapSchema(schemaPerson)

  val schemaObjMapAny: ObjectSchema = createObjSchema(schemaMapAny)

  val schemaObjMapInt: ObjectSchema = createObjSchema(schemaMapInt)

  val schemaObjInt: ObjectSchema = createObjSchema(schemaInteger)

  val schemaObjStr: ObjectSchema = createObjSchema(schemaString)

  val schemaObjNull: ObjectSchema = createObjSchema(schemaNull)

  val schemaObjUnionNullStr: ObjectSchema = createObjSchema(schemaNull, schemaString)

  val schemaObjMapObjPerson: ObjectSchema = createObjSchema(schemaMapObjPerson)


  /* SpEL sink output configuration */
  val sampleInt = 13

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

  val sampleJStr: Json = fromString(sampleStr)

  val samplePerson: Json = obj("first" -> fromString(strNu), "last" -> fromString(strTouK), "age" -> sampleJInt)

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

  def createArraySchema(schemas: Schema*): ArraySchema =
    ArraySchema.builder().allItemSchema(asSchema(schemas:_*)).build()

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
