package pl.touk.nussknacker.engine.api.typed

import io.circe.{Encoder, Json, JsonObject}
import io.circe.Json._
import pl.touk.nussknacker.engine.api.typed.typing._

object TypeEncoders {

  private def encodeTypedClass(ref: TypedClass): Json = obj(
    "refClazzName" -> fromString(ref.klass.getName),
    "params" -> fromValues(ref.params.map(encodeTypingResult))
  )

  //TODO: maybe we want to treat Unknown differently also on FE?
  private val encodeUnknown = obj("refClazzName" -> fromString(classOf[Object].getName), "params" -> fromValues(Nil))

  private def encodeTypingResult(result: TypingResult): Json = result match {
    case single: SingleTypingResult => encodeSingleTypingResult(single)
    case typing.Unknown => encodeUnknown
    case TypedUnion(classes) =>
      fromFields(("union" -> fromValues(classes.map(encodeTypingResult).toList))::Nil)
  }

  private def encodeSingleTypingResult(result: SingleTypingResult): Json = result match {
    case TypedObjectTypingResult(fields, objType) =>
      val objTypeEncoded = encodeTypedClass(objType).asObject.getOrElse(JsonObject.empty)
      val fieldsEncoded = "fields" -> fromFields(fields.mapValues(encodeTypingResult).toList)
      fromJsonObject(objTypeEncoded.+:(fieldsEncoded))
    case dict: TypedDict =>
      obj("dict" -> obj(
        "id" -> fromString(dict.dictId),
        "valueType" -> encodeSingleTypingResult(dict.valueType)))
    case TypedTaggedValue(underlying, tag) =>
      val objTypeEncoded = encodeSingleTypingResult(underlying).asObject.getOrElse(JsonObject.empty)
      val tagEncoded = "tag" -> fromString(tag)
      fromJsonObject(objTypeEncoded.+:(tagEncoded))
    case cl: TypedClass => encodeTypedClass(cl)
  }

  implicit val typingResultEncoder: Encoder[TypingResult] = Encoder.instance(encodeTypingResult)

}

