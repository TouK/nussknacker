package pl.touk.nussknacker.engine.json.swagger

import io.circe.generic.JsonCodec
import io.swagger.v3.oas.models.media.{ArraySchema, MapSchema, ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.json.swagger.parser.{PropertyName, SwaggerRefSchemas}

import java.time.{LocalDate, LocalTime, ZonedDateTime}
import java.util.Collections
import scala.annotation.tailrec
import scala.collection.JavaConverters._

@JsonCodec sealed trait AdditionalProperties
case object AdditionalPropertiesDisabled extends AdditionalProperties
case object AdditionalPropertiesWithoutType extends AdditionalProperties
case class AdditionalPropertiesSwaggerTyped(value: SwaggerTyped) extends AdditionalProperties

@JsonCodec sealed trait SwaggerTyped {
  self =>
  def typingResult: TypingResult =
    SwaggerTyped.typingResult(self)
}

case object SwaggerString extends SwaggerTyped

case object SwaggerBool extends SwaggerTyped

case object SwaggerLong extends SwaggerTyped

case object SwaggerDouble extends SwaggerTyped

case object SwaggerNull extends SwaggerTyped

case object SwaggerBigDecimal extends SwaggerTyped

case object SwaggerDateTime extends SwaggerTyped

case object SwaggerDate extends SwaggerTyped

case object SwaggerTime extends SwaggerTyped

case class SwaggerUnion(types: List[SwaggerTyped]) extends SwaggerTyped

case class SwaggerEnum(values: List[String]) extends SwaggerTyped //todo: rename to SwaggerStringEnum?

case class SwaggerArray(elementType: SwaggerTyped) extends SwaggerTyped

case class SwaggerObject(elementType: Map[PropertyName, SwaggerTyped], additionalProperties: AdditionalProperties = AdditionalPropertiesWithoutType) extends SwaggerTyped

case class SwaggerMap(valuesType: Option[SwaggerTyped]) extends SwaggerTyped

//mapped to Unknown in type system
sealed trait SwaggerUnknownFallback extends SwaggerTyped

case object SwaggerRecursiveSchema extends SwaggerUnknownFallback

case object SwaggerEnumOfVariousTypes extends SwaggerUnknownFallback

object SwaggerTyped {

  def apply(schema: Schema[_], swaggerRefSchemas: SwaggerRefSchemas): SwaggerTyped = apply(schema, swaggerRefSchemas, Set.empty)

  @tailrec
  private[swagger] def apply(schema: Schema[_], swaggerRefSchemas: SwaggerRefSchemas, usedSchemas: Set[String]): SwaggerTyped = schema match {
    case objectSchema: ObjectSchema => SwaggerObject(objectSchema, swaggerRefSchemas, usedSchemas)
    case mapSchema: MapSchema => SwaggerObject(mapSchema, swaggerRefSchemas, usedSchemas)
    case IsArraySchema(array) => SwaggerArray(array, swaggerRefSchemas, usedSchemas)
    case _ => Option(schema.get$ref()) match {
      //handle recursive schemas better
      case Some(ref) if usedSchemas.contains(ref) =>
        SwaggerRecursiveSchema
      case Some(ref) =>
        SwaggerTyped(swaggerRefSchemas(ref), swaggerRefSchemas, usedSchemas = usedSchemas + ref)
      case None => (extractType(schema), Option(schema.getFormat)) match {
        //TODO: we don't handle cases when anyOf/oneOf is *extension* of a schema (i.e. `schema` has properties)
        case (Some("object") | None, _) if Option(schema.getAnyOf).exists(!_.isEmpty) => swaggerUnion(schema.getAnyOf, swaggerRefSchemas, usedSchemas)
        // We do not track information whether is 'oneOf' or 'anyOf', as result of this method is used only for typing
        // Actual data validation is made in runtime in de/serialization layer and it is performed against actual schema, not our representation
        case (Some("object") | None, _) if Option(schema.getOneOf).exists(!_.isEmpty) => swaggerUnion(schema.getOneOf, swaggerRefSchemas, usedSchemas)
        case (Some("object") | None, _) => SwaggerObject(schema.asInstanceOf[Schema[Object@unchecked]], swaggerRefSchemas, usedSchemas)
        case (typ, _) if schema.getEnum != null =>
          val values = schema.getEnum.asScala
          if (values.forall(v => v.isInstanceOf[String]) && !typ.exists(_ != "string"))
            SwaggerEnum(values.flatMap(Option(_).toList).map(_.toString).toList)
          else
            SwaggerEnumOfVariousTypes //todo: add support for enums of various types
        case (Some("boolean"), _) => SwaggerBool
        case (Some("string"), Some("date-time")) => SwaggerDateTime
        case (Some("string"), Some("date")) => SwaggerDate
        case (Some("string"), Some("time")) => SwaggerTime
        case (Some("string"), _) => SwaggerString
        case (Some("integer"), _) => SwaggerLong
        //we refuse to accept invalid formats (e.g. integer, int32, decimal etc.)
        case (Some("number"), None) => SwaggerBigDecimal
        case (Some("number"), Some("double")) => SwaggerDouble
        case (Some("number"), Some("float")) => SwaggerDouble
        case (Some("null"), None) => SwaggerNull
        case (typeName, format) =>
          val formatError = format.map(f => s" in format '$f'").getOrElse("")
          throw new Exception(s"Type '${typeName.getOrElse("empty")}'$formatError is not supported")
      }
    }
  }

  private object IsArraySchema {
    def unapply(schema: Schema[_]): Option[Schema[_]] = schema match {
      case a: ArraySchema => Some(a)
      //this is how OpenAPI is parsed when `type: array` is used
      case oth if Option(oth.getTypes).exists(_.equals(Collections.singleton("array"))) && oth.getItems != null => Some(oth)
      case _ => None
    }
  }

  private def swaggerUnion(schemas: java.util.List[Schema[_]], swaggerRefSchemas: SwaggerRefSchemas, usedSchemas: Set[String]) = SwaggerUnion(schemas.asScala.map(SwaggerTyped(_, swaggerRefSchemas, usedSchemas)).toList)

  private def extractType(schema: Schema[_]): Option[String] =
    Option(schema.getType)
      .orElse(Option(schema.getTypes).map(_.asScala.head))

  def typingResult(swaggerTyped: SwaggerTyped): TypingResult = swaggerTyped match {
    case SwaggerMap(valueType: Option[SwaggerTyped]) =>
      Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], valueType.map(typingResult).getOrElse(Unknown)))
    case SwaggerObject(elementType, _) =>
      import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
      TypedObjectTypingResult(elementType.mapValuesNow(typingResult).toList.sortBy(_._1))
    case SwaggerArray(ofType) =>
      Typed.genericTypeClass(classOf[java.util.List[_]], List(typingResult(ofType)))
    case SwaggerEnum(values) =>
      Typed(values.map(Typed.fromInstance).toSet)
    case SwaggerBool =>
      Typed.typedClass[java.lang.Boolean]
    case SwaggerString =>
      Typed.typedClass[String]
    case SwaggerLong =>
      Typed.typedClass[java.lang.Long]
    case SwaggerDouble =>
      Typed.typedClass[java.lang.Double]
    case SwaggerBigDecimal =>
      Typed.typedClass[java.math.BigDecimal]
    case SwaggerDateTime =>
      Typed.typedClass[ZonedDateTime]
    case SwaggerDate =>
      Typed.typedClass[LocalDate]
    case SwaggerTime =>
      Typed.typedClass[LocalTime]
    case SwaggerUnion(types) => Typed(types.map(typingResult).toSet)
    case _: SwaggerUnknownFallback =>
      Unknown
    case SwaggerNull =>
      TypedNull
  }
}
object SwaggerArray {
  private[swagger] def apply(schema: Schema[_], swaggerRefSchemas: SwaggerRefSchemas, usedRefs: Set[String]): SwaggerArray =
    SwaggerArray(elementType = SwaggerTyped(schema.getItems, swaggerRefSchemas, usedRefs))
}

object SwaggerObject {
  private[swagger] def apply(schema: Schema[Object], swaggerRefSchemas: SwaggerRefSchemas, usedRefs: Set[String]): SwaggerTyped = {
    val properties = Option(schema.getProperties).map(_.asScala.mapValues(SwaggerTyped(_, swaggerRefSchemas, usedRefs)).toMap).getOrElse(Map())

    if (properties.isEmpty) {
      schema.getAdditionalProperties match {
        case a: Schema[_] => SwaggerMap(Some(SwaggerTyped(a, swaggerRefSchemas, usedRefs)))
        case b if b == false => new SwaggerObject(Map.empty, AdditionalPropertiesDisabled)
        case _ => SwaggerMap(None)
      }
    } else{
      val additionalProperties = schema.getAdditionalProperties match {
        case a: Schema[_] => AdditionalPropertiesSwaggerTyped(SwaggerTyped(a, swaggerRefSchemas, usedRefs))
        case any if any == false => AdditionalPropertiesDisabled
        case _ => AdditionalPropertiesWithoutType
      }

      SwaggerObject(properties, additionalProperties)
    }
  }
}
