package pl.touk.nussknacker.openapi

import io.circe.generic.JsonCodec

import java.time.LocalDateTime
import io.swagger.v3.oas.models.media.{ArraySchema, MapSchema, ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypedClass, TypedObjectTypingResult}
import pl.touk.nussknacker.openapi.parser.SwaggerRefSchemas

import scala.annotation.tailrec
import scala.collection.JavaConverters._

@JsonCodec sealed trait SwaggerTyped { self =>
  def typingResult: SingleTypingResult =
    SwaggerTyped.typingResult(self)
}

case object SwaggerString extends SwaggerTyped

case object SwaggerBool extends SwaggerTyped

case object SwaggerLong extends SwaggerTyped

case object SwaggerDouble extends SwaggerTyped

case object SwaggerBigDecimal extends SwaggerTyped

case object SwaggerDateTime extends SwaggerTyped

case class SwaggerEnum(values: List[String]) extends SwaggerTyped

case class SwaggerArray(elementType: SwaggerTyped) extends SwaggerTyped

case class SwaggerObject(elementType: Map[PropertyName, SwaggerTyped], required: Set[PropertyName]) extends SwaggerTyped

object SwaggerTyped {

  @tailrec
  def apply(schema: Schema[_], swaggerRefSchemas: SwaggerRefSchemas): SwaggerTyped = schema match {
    case objectSchema: ObjectSchema => SwaggerObject(objectSchema, swaggerRefSchemas)
    case mapSchema: MapSchema => SwaggerObject(mapSchema, swaggerRefSchemas)
    case arraySchema: ArraySchema => SwaggerArray(arraySchema, swaggerRefSchemas)
    case _ => Option(schema.get$ref()) match {
      case Some(ref) =>
        SwaggerTyped(swaggerRefSchemas(ref), swaggerRefSchemas)
      case None => (schema.getType, Option(schema.getFormat)) match {
        case ("boolean", _) => SwaggerBool
        case ("string", Some("date-time")) => SwaggerDateTime
        case ("string", _) => Option(schema.getEnum) match {
          case Some(values) => SwaggerEnum(values.asScala.map(_.toString).toList)
          case None => SwaggerString
        }
        case ("integer", _) => SwaggerLong
        case ("number", None) => SwaggerBigDecimal
        case ("number", Some("double")) => SwaggerDouble
        case ("number", Some("float")) => SwaggerDouble
        case (typeName, format) => throw new Exception(s"Type $typeName in format: $format, is not supported")
      }
    }
  }

  def typingResult(swaggerTyped: SwaggerTyped): SingleTypingResult = swaggerTyped match {
    case SwaggerObject(elementType, _) =>
      import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
      TypedObjectTypingResult(elementType.mapValuesNow(typingResult).toList.sortBy(_._1))
    case SwaggerArray(ofType) =>
      TypedClass(classOf[java.util.List[_]], List(typingResult(ofType)))
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
      required = Option(schema.getRequired).map(_.asScala.toSet).getOrElse(Set.empty)
    )
  }
}





