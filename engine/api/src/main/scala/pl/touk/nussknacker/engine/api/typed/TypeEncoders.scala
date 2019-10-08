package pl.touk.nussknacker.engine.api.typed

import io.circe.{Encoder, Json, JsonObject}
import io.circe.Json._
import pl.touk.nussknacker.engine.api.typed.typing._

object TypeEncoders {

  private def encodeTypedClass(ref: TypedClass): Json = obj(
    "refClazzName" -> fromString(ref.klass.getName),
    "params" -> fromValues(ref.params.map(encodeTypingResult))
  )

  private def encodeTypingResult(result: TypingResult): Json = result match {
    case typing.Unknown => encodeTypedClass(TypedClass[Any])
    case TypedUnion(classes) =>
      fromFields(("union" -> fromValues(classes.map(encodeTypingResult).toList))::Nil)
    case TypedObjectTypingResult(fields, objType) =>
      //TODO: check if after objType was added still happens: map methods are suggested but validation fails?
      val objTypeEncoded = encodeTypedClass(objType).asObject.getOrElse(JsonObject.empty)
      val fieldsEncoded = "fields" -> fromFields(fields.mapValues(encodeTypingResult).toList)
      fromJsonObject(objTypeEncoded.+:(fieldsEncoded))
    case _: TypedDict => ??? // FIXME: handle
    case cl: TypedClass => encodeTypedClass(cl)
  }

  implicit val clazzRefEncoder: Encoder[ClazzRef] = Encoder.instance(tc => encodeTypingResult(Typed(tc)))

  implicit val typingResultEncoder: Encoder[TypingResult] = Encoder.instance(encodeTypingResult)

}

