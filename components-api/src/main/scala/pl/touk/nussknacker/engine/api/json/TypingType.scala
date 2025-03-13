package pl.touk.nussknacker.engine.api.json

import io.circe._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing._

private[json] object TypingType extends Enumeration {

  private[json] val typeField = "type"

  implicit val decoder: Decoder[TypingType.Value] = Decoder.decodeEnumeration(TypingType)

  type TypingType = Value

  val TypedUnion, TypedDict, TypedObjectTypingResult, TypedTaggedValue, TypedClass, TypedObjectWithValue, TypedNull,
      Unknown = Value

  def forType(typingResult: TypingResult): TypingType.Value = typingResult match {
    case _: TypedClass              => TypedClass
    case _: TypedUnion              => TypedUnion
    case _: TypedDict               => TypedDict
    case _: TypedObjectTypingResult => TypedObjectTypingResult
    case _: TypedTaggedValue        => TypedTaggedValue
    case _: TypedObjectWithValue    => TypedObjectWithValue
    case typing.TypedNull           => TypedNull
    case typing.Unknown             => Unknown
  }

}
