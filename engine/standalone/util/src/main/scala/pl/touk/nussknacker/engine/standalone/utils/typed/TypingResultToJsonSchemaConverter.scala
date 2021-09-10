package pl.touk.nussknacker.engine.standalone.utils.typed

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult}

class TypingResultToJsonSchemaConverter(exampleValues: Map[String, Json]) extends LazyLogging {

  def typingResultToJsonSchema(typingResult: TypingResult, paramName: Option[String] = None): Map[String, Any] = typingResult match {
    case typed: TypedObjectTypingResult => typed.fields.map(v => v._1 -> typingResultToJsonSchema(v._2, Some(v._1)))
    case typed: TypingResult => translateTypeResult(typed, paramName.flatMap(exampleValues.get))
  }

  private def translateTypeResult(typingResult: TypingResult, example: Option[Json] = None): Map[String, Json] = {
    (typingResult match {
      case TypedClass(klass, _) if klass == classOf[java.lang.String] =>
        Map("type" -> "string".asJson)
      case TypedClass(klass, _) if klass == classOf[java.lang.Boolean] =>
        Map("type" -> "boolean".asJson)
      case TypedClass(klass, _) if klass == classOf[java.lang.Integer] =>
        Map("type" -> "integer".asJson)
      case TypedClass(klass, _) if klass == classOf[java.lang.Number] =>
        Map("type" -> "number".asJson)
      case TypedClass(klass, params) if klass == classOf[java.util.List[_]] =>
        Map("type" -> "array".asJson, "items" -> params.flatMap(p => translateTypeResult(p)).toMap.asJson)
      case TypedClass(klass, params) if klass == classOf[java.util.Map[_, _]] =>
        Map("type" -> "object".asJson, "additionalProperties" -> params.flatMap(p => translateTypeResult(p)).toMap.asJson)
      case _ => Map()
    }) ++ example.fold(Map.empty[String, Json])(e => Map("example" -> e.asJson))
  }

}