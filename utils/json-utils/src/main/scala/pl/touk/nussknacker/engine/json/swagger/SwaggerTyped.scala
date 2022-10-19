package pl.touk.nussknacker.engine.json.swagger

import io.circe.generic.JsonCodec
import io.swagger.v3.oas.models.media.{ArraySchema, MapSchema, ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedNull, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.json.swagger.parser.{PropertyName, SwaggerRefSchemas}

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.annotation.tailrec
import scala.collection.JavaConverters._

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

case class SwaggerEnum(values: List[String]) extends SwaggerTyped

case class SwaggerArray(elementType: SwaggerTyped) extends SwaggerTyped

case class SwaggerObject(elementType: Map[PropertyName, SwaggerTyped],
                         required: Set[PropertyName],
                         additionalProperties: Option[SwaggerTyped] = None,
                        ) extends SwaggerTyped

object SwaggerTyped {

  @tailrec
  def apply(schema: Schema[_], swaggerRefSchemas: SwaggerRefSchemas): SwaggerTyped = schema match {
    case objectSchema: ObjectSchema => SwaggerObject(objectSchema, swaggerRefSchemas)
    case mapSchema: MapSchema => SwaggerObject(mapSchema, swaggerRefSchemas)
    case arraySchema: ArraySchema => SwaggerArray(arraySchema, swaggerRefSchemas)
    case _ => Option(schema.get$ref()) match {
      case Some(ref) =>
        SwaggerTyped(swaggerRefSchemas(ref), swaggerRefSchemas)
      case None => (extractType(schema), Option(schema.getFormat)) match {
        case (None, _) => SwaggerObject(schema.asInstanceOf[Schema[Object@unchecked]], swaggerRefSchemas)
        case (Some("object"), _) => SwaggerObject(schema.asInstanceOf[ObjectSchema], swaggerRefSchemas)
        case (Some("boolean"), _) => SwaggerBool
        case (Some("string"), Some("date-time")) => SwaggerDateTime
        case (Some("string"), Some("date")) => SwaggerDate
        case (Some("string"), Some("time")) => SwaggerTime
        case (Some("string"), _) => Option(schema.getEnum) match {
          case Some(values) => SwaggerEnum(values.asScala.map(_.toString).toList)
          case None => SwaggerString
        }
        case (Some("integer"), _) => SwaggerLong
        case (Some("number"), None) => SwaggerBigDecimal
        case (Some("number"), Some("double")) => SwaggerDouble
        case (Some("number"), Some("float")) => SwaggerDouble
        case (Some("null"), None) => SwaggerNull
        //todo handle unions
        case (typeName, format) => throw new Exception(s"Type $typeName in format: $format, is not supported")
      }
    }
  }

  private def extractType(schema: Schema[_]): Option[String] =
    Option(schema.getType)
      .orElse(Option(schema.getTypes).map(_.asScala.head))

  def typingResult(swaggerTyped: SwaggerTyped): TypingResult = swaggerTyped match {
    //todo what to do with additionalProperties
    case SwaggerObject(elementType, _, additionalProperties) =>
      import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
      TypedObjectTypingResult(
        elementType.mapValuesNow(typingResult).toList.sortBy(_._1) ++
          additionalProperties.map(prop=>List("additionalProperties"-> typingResult(prop))).getOrElse(List())
      )
    case SwaggerArray(ofType) =>
      Typed.genericTypeClass(classOf[java.util.List[_]], List(typingResult(ofType)))
    case SwaggerEnum(_) =>
      Typed.typedClass[String]
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
      Typed.typedClass[LocalDateTime]
    case SwaggerDate =>
      Typed.typedClass[LocalDate]
    case SwaggerTime =>
      Typed.typedClass[LocalTime]
    case SwaggerNull =>
      TypedNull
  }
}

object SwaggerArray {
  def apply(schema: ArraySchema, swaggerRefSchemas: SwaggerRefSchemas): SwaggerArray =
    SwaggerArray(elementType = SwaggerTyped(schema.getItems, swaggerRefSchemas))
}

object SwaggerObject {
  def apply(schema: Schema[Object], swaggerRefSchemas: SwaggerRefSchemas): SwaggerObject = {
    SwaggerObject(
      elementType = Option(schema.getProperties).map(_.asScala.mapValues(SwaggerTyped(_, swaggerRefSchemas)).toMap).getOrElse(Map()),
      additionalProperties = schema.getAdditionalProperties match {
        case a: Schema[_] => Some(SwaggerTyped(a, swaggerRefSchemas))
        case _ => None
      },
      required = Option(schema.getRequired).map(_.asScala.toSet).getOrElse(Set.empty)
    )
  }
}
