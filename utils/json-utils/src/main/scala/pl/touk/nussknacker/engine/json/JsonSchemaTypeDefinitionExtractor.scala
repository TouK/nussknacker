package pl.touk.nussknacker.engine.json

import org.everit.json.schema._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedNull, TypedObjectTypingResult, TypingResult}

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`

class JsonSchemaTypeDefinitionExtractor {

  private val propertiesField = "properties"

  def typeDefinition(schema: Schema): TypingResult = {
    schema match {
      case os: ObjectSchema => parseObjectSchema(os)
      case _ => resolveJsonTypingResult(schema) //single value is required
    }
  }

  private def parseObjectSchema(schema: ObjectSchema): TypedObjectTypingResult = {
    require(!schema.getPropertySchemas.isEmpty, s"""ObjectSchema requires \"$propertiesField\" field.""")
    jsonValuesToTypingResult(schema.getPropertySchemas.asScala.toMap)
  }

  private def jsonValuesToTypingResult(namedSchema: Map[String, Schema]): TypedObjectTypingResult = {
    TypedObjectTypingResult(namedSchema.mapValues(resolveJsonTypingResult).toList)
  }

  private[json] def resolveJsonTypingResult(schema: Schema): TypingResult = {
    schema match {
      case s: ArraySchema => getArrayTypingResult(s)
      case s: ObjectSchema => parseObjectSchema(s)
      case s: ReferenceSchema => parseReference(s)
      case s: CombinedSchema => parseCombinedSchema(s)
      case s => resolveSimpleTypingResult(s)
    }
  }

  private def resolveStringWithFormat(schema: StringSchema): TypingResult = {
    schema.getFormatValidator.formatName() match {
      case "date-time" => Typed.typedClass[LocalDateTime]
      case "date" => Typed.typedClass[LocalDate]
      case "time" => Typed.typedClass[LocalTime]
      case _ => Typed.typedClass[String]
    }
  }

  def parseCombinedSchema(combined: CombinedSchema): TypingResult = combined.getSubschemas.toList match {
    case Nil => TypedNull
    case s :: Nil => typeDefinition(s)
    case List(a: StringSchema, _: EnumSchema) => typeDefinition(a)
    case List(_: EnumSchema, b: StringSchema) => typeDefinition(b)
    case _ => throw new IllegalArgumentException(s"Schema '$combined' is not supported yet.")
  }

  def parseReference(s: ReferenceSchema): TypingResult = {
    typeDefinition(s.getReferredSchema)
  }

  private def resolveSimpleTypingResult(schema: Schema): TypingResult = {
    schema match {
      case s: NumberSchema => if (s.requiresInteger()) Typed.typedClass[java.lang.Long] else Typed.typedClass[java.math.BigDecimal]
      case _: BooleanSchema => Typed.typedClass[Boolean]
      case _: TrueSchema => Typed.typedClass[Boolean]
      case _: FalseSchema => Typed.typedClass[Boolean]
      case _: NullSchema => TypedNull
      case s: StringSchema => resolveStringWithFormat(s)
      case _: EnumSchema => Typed.typedClass[String]
      case s => throw new IllegalArgumentException(s"Schema '$s' is not supported yet.")
    }
  }

  private def getArrayTypingResult(schema: ArraySchema): TypingResult = {
    val schemaWithOptionalList = List(resolveJsonTypingResult(schema.getAllItemSchema))
    Typed.genericTypeClass[java.util.List[_]](schemaWithOptionalList)
  }

}

object JsonSchemaTypeDefinitionExtractor {

  private lazy val fieldsExtractor = new JsonSchemaTypeDefinitionExtractor

  def typeDefinition(schema: Schema): TypingResult =
    fieldsExtractor.typeDefinition(schema)

}