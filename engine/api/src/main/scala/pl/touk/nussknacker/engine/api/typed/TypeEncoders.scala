package pl.touk.nussknacker.engine.api.typed

import argonaut.Argonaut._
import argonaut._
import pl.touk.nussknacker.engine.api.typed.typing._

object TypeEncoders {

  private def encodeTypedClass(ref: TypedClass): Json = jObjectFields(
    "refClazzName" -> jString(ref.klass.getName),
    "params" -> jArray(ref.params.map(encodeTypingResult))
  )

  private def encodeTypingResult(result: TypingResult): Json = result match {
    case typing.Unknown => encodeTypedClass(TypedClass(classOf[Any], List()))
    case Typed(classes) => val headClass = classes.head
      encodeTypedClass(headClass)
    case TypedMapTypingResult(fields) => jObjectAssocList(
      //FIXME: this assumes that typed map is a map - it's not always the case, map methods are suggested but validation fails?
      encodeTypedClass(TypedClass[java.util.Map[_, _]]).objectOrEmpty.toList
        :+ "fields" -> jObjectAssocList(fields.mapValues(encodeTypingResult).toList)
    )
  }

  implicit val clazzRefEncoder: EncodeJson[ClazzRef] = EncodeJson[ClazzRef](tc => encodeTypedClass(TypedClass(tc)))

  implicit val typingResultEncoder: EncodeJson[TypingResult] = EncodeJson[TypingResult](encodeTypingResult)

}

