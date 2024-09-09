package pl.touk.nussknacker.engine.json.swagger

import cats.data.NonEmptyList
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.swagger.v3.oas.models.media.{ArraySchema, MapSchema, ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.json.swagger.parser.{PropertyName, SwaggerRefSchemas}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.json.JsonUtils.jsonToAny
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import java.time.{LocalDate, LocalTime, ZonedDateTime}
import java.util
import java.util.Collections
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

@JsonCodec sealed trait AdditionalProperties
case object AdditionalPropertiesDisabled                    extends AdditionalProperties
case class AdditionalPropertiesEnabled(value: SwaggerTyped) extends AdditionalProperties

@JsonCodec sealed trait SwaggerTyped {
  self =>
  def typingResult: TypingResult =
    SwaggerTyped.typingResult(self)
}

case object SwaggerString extends SwaggerTyped

case object SwaggerBool extends SwaggerTyped

case object SwaggerLong extends SwaggerTyped

case object SwaggerInteger extends SwaggerTyped

case object SwaggerBigInteger extends SwaggerTyped

case object SwaggerDouble extends SwaggerTyped

case object SwaggerNull extends SwaggerTyped

case object SwaggerBigDecimal extends SwaggerTyped

case object SwaggerDateTime extends SwaggerTyped

case object SwaggerDate extends SwaggerTyped

case object SwaggerTime extends SwaggerTyped

case class SwaggerUnion(types: List[SwaggerTyped]) extends SwaggerTyped

@JsonCodec case class SwaggerEnum private (values: List[Any]) extends SwaggerTyped

object SwaggerEnum {
  private lazy val om       = new ObjectMapper()
  private val toJsonEncoder = ToJsonEncoder(failOnUnknown = true, getClass.getClassLoader)
  private implicit val listDecoder: Decoder[List[Any]] =
    Decoder[Json].map(_.asArray.map(_.toList.map(jsonToAny)).getOrElse(List.empty))
  private implicit val listEncoder: Encoder[List[Any]] = Encoder.instance[List[Any]](toJsonEncoder.encode)

  def apply(schema: Schema[_]): SwaggerEnum = {
    val list = schema.getEnum.asScala.toList.map {
      case j: ObjectNode => om.convertValue(j, new TypeReference[java.util.Map[String, Any]]() {})
      case j: ArrayNode  => om.convertValue(j, new TypeReference[java.util.List[Any]]() {})
      case any           => any
    }
    SwaggerEnum(list)
  }

}

case class SwaggerArray(elementType: SwaggerTyped) extends SwaggerTyped

@JsonCodec case class SwaggerObject(
    elementType: Map[PropertyName, SwaggerTyped],
    additionalProperties: AdditionalProperties = AdditionalPropertiesEnabled(SwaggerAny),
    patternProperties: List[PatternWithSwaggerTyped] = List.empty
) extends SwaggerTyped

@JsonCodec case class PatternWithSwaggerTyped(pattern: String, propertyType: SwaggerTyped) {
  private lazy val compiledPattern: Pattern = Pattern.compile(pattern)

  def testPropertyName(propertyName: PropertyName): Boolean = {
    compiledPattern.asPredicate().test(propertyName)
  }

}

//mapped to Unknown in type system
object SwaggerAny extends SwaggerTyped

object SwaggerTyped {
  def apply(schema: Schema[_], swaggerRefSchemas: SwaggerRefSchemas): SwaggerTyped =
    apply(schema, swaggerRefSchemas, Set.empty)

  @tailrec
  private[swagger] def apply(
      schema: Schema[_],
      swaggerRefSchemas: SwaggerRefSchemas,
      usedSchemas: Set[String]
  ): SwaggerTyped = schema match {
    case objectSchema: ObjectSchema => SwaggerObject(objectSchema, swaggerRefSchemas, usedSchemas)
    case mapSchema: MapSchema       => SwaggerObject(mapSchema, swaggerRefSchemas, usedSchemas)
    case IsArraySchema(array)       => SwaggerArray(array, swaggerRefSchemas, usedSchemas)
    case _ =>
      Option(schema.get$ref()) match {
        // handle recursive schemas better
        case Some(ref) if usedSchemas.contains(ref) =>
          SwaggerAny
        case Some(ref) =>
          SwaggerTyped(swaggerRefSchemas(ref), swaggerRefSchemas, usedSchemas = usedSchemas + ref)
        case None =>
          (extractType(schema), Option(schema.getFormat)) match {
            case (_, _) if schema.getEnum != null => SwaggerEnum(schema)
            // TODO: we don't handle cases when anyOf/oneOf is *extension* of a schema (i.e. `schema` has properties)
            case (Some("object") | None, _) if Option(schema.getAnyOf).exists(!_.isEmpty) =>
              swaggerUnion(schema.getAnyOf, swaggerRefSchemas, usedSchemas)
            // We do not track information whether is 'oneOf' or 'anyOf', as result of this method is used only for typing
            // Actual data validation is made in runtime in de/serialization layer and it is performed against actual schema, not our representation
            case (Some("object") | None, _) if Option(schema.getOneOf).exists(!_.isEmpty) =>
              swaggerUnion(schema.getOneOf, swaggerRefSchemas, usedSchemas)
            case (Some("object"), _) =>
              SwaggerObject(schema.asInstanceOf[Schema[Object @unchecked]], swaggerRefSchemas, usedSchemas)
            case (Some("boolean"), _)                => SwaggerBool
            case (Some("string"), Some("date-time")) => SwaggerDateTime
            case (Some("string"), Some("date"))      => SwaggerDate
            case (Some("string"), Some("time"))      => SwaggerTime
            case (Some("string"), _)                 => SwaggerString
            case (Some("integer"), _) =>
              inferredIntType(
                schema.getMinimum,
                schema.getExclusiveMinimumValue,
                schema.getMaximum,
                schema.getExclusiveMaximumValue
              )
            // we refuse to accept invalid formats (e.g. integer, int32, decimal etc.)
            case (Some("number"), None)           => SwaggerBigDecimal
            case (Some("number"), Some("double")) => SwaggerDouble
            case (Some("number"), Some("float"))  => SwaggerDouble
            case (Some("null"), None)             => SwaggerNull
            case (None, _)                        => SwaggerAny
            case (typeName, format) =>
              val formatError = format.map(f => s" in format '$f'").getOrElse("")
              throw new Exception(s"Type '${typeName.getOrElse("empty")}'$formatError is not supported")
          }
      }
  }

  private object IsArraySchema {

    def unapply(schema: Schema[_]): Option[Schema[_]] = schema match {
      case a: ArraySchema => Some(a)
      // this is how OpenAPI is parsed when `type: array` is used
      case oth if Option(oth.getTypes).exists(_.equals(Collections.singleton("array"))) && oth.getItems != null =>
        Some(oth)
      case _ => None
    }

  }

  private def swaggerUnion(
      schemas: java.util.List[Schema[_]],
      swaggerRefSchemas: SwaggerRefSchemas,
      usedSchemas: Set[String]
  ) = SwaggerUnion(schemas.asScala.map(SwaggerTyped(_, swaggerRefSchemas, usedSchemas)).toList)

  private def extractType(schema: Schema[_]): Option[String] =
    Option(schema.getType)
      .orElse(Option(schema.getTypes).map(_.asScala.head))

  // `resolveListOfObjects` flag allows one to stop resolving Type recursion for  SwaggerArray[SwaggerObject]
  // this is needed for correct validations in openApi enrichers with input parameters that contains list of objects with optional fields
  // TODO: validations in openApi enrichers should be based on actual schema instead of `TypingResult` instance
  def typingResult(swaggerTyped: SwaggerTyped, resolveListOfObjects: Boolean = true): TypingResult =
    swaggerTyped match {
      case SwaggerObject(elementType, additionalProperties, patternProperties) =>
        handleSwaggerObject(elementType, additionalProperties, patternProperties, resolveListOfObjects)
      case SwaggerArray(SwaggerObject(_, _, _)) if !resolveListOfObjects =>
        Typed.genericTypeClass(classOf[java.util.List[_]], List(Unknown))
      case SwaggerArray(ofType) =>
        Typed.genericTypeClass(classOf[java.util.List[_]], List(typingResult(ofType, resolveListOfObjects)))
      case SwaggerEnum(values) =>
        Typed.fromIterableOrUnknownIfEmpty(values.map(Typed.fromInstance))
      case SwaggerBool =>
        Typed.typedClass[java.lang.Boolean]
      case SwaggerString =>
        Typed.typedClass[String]
      case SwaggerInteger =>
        Typed.typedClass[java.lang.Integer]
      case SwaggerLong =>
        Typed.typedClass[java.lang.Long]
      case SwaggerBigInteger =>
        Typed.typedClass[java.math.BigInteger]
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
      case SwaggerUnion(types) => Typed.fromIterableOrUnknownIfEmpty(types.map(typingResult(_, resolveListOfObjects)))
      case SwaggerAny =>
        Unknown
      case SwaggerNull =>
        TypedNull
    }

  private def handleSwaggerObject(
      elementType: Map[PropertyName, SwaggerTyped],
      additionalProperties: AdditionalProperties,
      patternProperties: List[PatternWithSwaggerTyped],
      resolveListOfObject: Boolean = true
  ): TypingResult = {
    import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
    def typedStringKeyMap(valueType: TypingResult) = {
      Typed.genericTypeClass(classOf[util.Map[_, _]], List(Typed[PropertyName], valueType))
    }
    if (elementType.isEmpty) {
      val patternPropertiesTypesSet = patternProperties.map { case PatternWithSwaggerTyped(_, propertySwaggerTyped) =>
        typingResult(propertySwaggerTyped, resolveListOfObject)
      }
      additionalProperties match {
        case AdditionalPropertiesDisabled if patternPropertiesTypesSet.isEmpty =>
          Typed.record(Map.empty[String, TypingResult])
        case AdditionalPropertiesDisabled =>
          typedStringKeyMap(Typed.fromIterableOrUnknownIfEmpty(patternPropertiesTypesSet))
        case AdditionalPropertiesEnabled(value) =>
          typedStringKeyMap(Typed(NonEmptyList(typingResult(value, resolveListOfObject), patternPropertiesTypesSet)))
      }
    } else {
      Typed.record(elementType.mapValuesNow(typingResult(_, resolveListOfObject)))
    }
  }

  private def inferredIntType(
      minValue: java.math.BigDecimal,
      exclusiveMinValue: java.math.BigDecimal,
      maxValue: java.math.BigDecimal,
      exclusiveMaxValue: java.math.BigDecimal
  ): SwaggerTyped = {

    val lowerBoundary: Option[BigDecimal] = List(
      Option(exclusiveMinValue).map(_.add(java.math.BigDecimal.ONE)),
      Option(minValue)
    ).flatten.map(bd => bd: BigDecimal).sorted(Ordering.BigDecimal.reverse).headOption
    val upperBoundary: Option[BigDecimal] = List(
      Option(exclusiveMaxValue).map(_.subtract(java.math.BigDecimal.ONE)),
      Option(maxValue)
    ).flatten.map(bd => bd: BigDecimal).sorted(Ordering.BigDecimal).headOption

    // We try to keep inferred type as narrow as possible,
    // but in the case when at least one boundary exceeds Long range we end up with BigInteger type.
    // That can have negative performance impact and can be inconvenient to use.
    List(lowerBoundary, upperBoundary).flatten match {
      case min :: max :: Nil if min.isValidInt && max.isValidInt   => SwaggerInteger
      case min :: max :: Nil if min.isValidLong && max.isValidLong => SwaggerLong
      case _ :: _ :: Nil                                           => SwaggerBigInteger
      case value :: Nil if !value.isValidLong                      => SwaggerBigInteger
      case _                                                       => SwaggerLong
    }
  }

}

object SwaggerArray {

  private[swagger] def apply(
      schema: Schema[_],
      swaggerRefSchemas: SwaggerRefSchemas,
      usedRefs: Set[String]
  ): SwaggerArray =
    SwaggerArray(elementType = SwaggerTyped(schema.getItems, swaggerRefSchemas, usedRefs))

}

object SwaggerObject {

  private[swagger] def apply(
      schema: Schema[Object],
      swaggerRefSchemas: SwaggerRefSchemas,
      usedRefs: Set[String]
  ): SwaggerTyped = {
    val properties = Option(schema.getProperties)
      .map(_.asScala.toMap.mapValuesNow(SwaggerTyped(_, swaggerRefSchemas, usedRefs)).toMap)
      .getOrElse(Map())
    val patternProperties = Option(schema.getPatternProperties)
      .map(_.asScala.toList)
      .getOrElse(List.empty)
      .map { case (patternString, patternPropertySchema) =>
        PatternWithSwaggerTyped(patternString, SwaggerTyped(patternPropertySchema, swaggerRefSchemas, usedRefs))
      }

    val additionalProperties = schema.getAdditionalProperties match {
      case null                                                       => AdditionalPropertiesEnabled(SwaggerAny)
      case schema: Schema[_] if schema.getBooleanSchemaValue == false => AdditionalPropertiesDisabled
      case schema: Schema[_] => AdditionalPropertiesEnabled(SwaggerTyped(schema, swaggerRefSchemas, usedRefs))
      case additionalPropertyEnabled if additionalPropertyEnabled == true  => AdditionalPropertiesEnabled(SwaggerAny)
      case additionalPropertyEnabled if additionalPropertyEnabled == false => AdditionalPropertiesDisabled
    }
    SwaggerObject(properties, additionalProperties, patternProperties)
  }

}
