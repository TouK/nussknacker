package pl.touk.nussknacker.engine.api.json.encoders

import io.circe._
import io.circe.Json._
import pl.touk.nussknacker.engine.api.json.TypingType
import pl.touk.nussknacker.engine.api.json.TypingType.typeField
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

//TODO: refactor way of encoding to easier handle decoding.

// If changes are made to Encoders/Decoders should also change Schemas in NodesApiEndpoints.TypingDtoSchemas for OpenApi
object TypeEncoders {

  private def encodeTypedClass(ref: TypedClass): JsonObject = JsonObject(
    "refClazzName" -> fromString(ref.klass.getName),
    "params"       -> fromValues(ref.params.map(typ => fromJsonObject(encodeTypingResult(typ))))
  )

  private val encodeUnknown =
    JsonObject("refClazzName" -> fromString(classOf[Object].getName), "params" -> fromValues(Nil))

  // Object class is used because Null can represent any type.
  private val encodeNull =
    JsonObject("refClazzName" -> fromString(classOf[Object].getName), "params" -> fromValues(Nil))

  private def encodeTypingResult(result: TypingResult): JsonObject =
    (result match {
      case single: SingleTypingResult => encodeSingleTypingResult(single)
      case typing.Unknown(_)          => encodeUnknown
      case typing.TypedNull           => encodeNull
      case union: TypedUnion =>
        JsonObject(
          "union" -> fromValues(union.possibleTypes.map(typ => fromJsonObject(encodeTypingResult(typ))).toList)
        )
    })
      .+:(typeField -> fromString(TypingType.forType(result).toString))
      .+:("display" -> fromString(result.display))

  private def encodeSingleTypingResult(result: SingleTypingResult): JsonObject = result match {
    case TypedObjectTypingResult(fields, objType, additionalInfo) =>
      val objTypeEncoded = encodeTypedClass(objType)
      val fieldsEncoded =
        "fields" -> fromFields(fields.mapValuesNow(typ => fromJsonObject(encodeTypingResult(typ))))
      val standardFields = objTypeEncoded.+:(fieldsEncoded)
      if (additionalInfo.isEmpty) {
        standardFields
      } else {
        standardFields.+:(
          "additionalInfo" -> implicitly[Encoder[Map[String, AdditionalDataValue]]].apply(additionalInfo)
        )
      }
    case dict: TypedDict =>
      JsonObject(
        "dict" -> obj(
          "id"        -> fromString(dict.dictId),
          "valueType" -> fromJsonObject(encodeTypingResult(dict.valueType))
        )
      )
    case TypedTaggedValue(underlying, tag) =>
      val objTypeEncoded = encodeTypingResult(underlying)
      val tagEncoded     = "tag" -> fromString(tag)
      objTypeEncoded.+:(tagEncoded)
    case TypedObjectWithValue(underlying, value) =>
      val objTypeEncoded = encodeTypingResult(underlying)
      val dataEncoded: (String, Json) = "value" -> ToJsonEncoderWithFallback
        .encodeValue(value)
        .getOrElse(throw new IllegalStateException(s"Not supported data value: $value"))

      objTypeEncoded.+:(dataEncoded)
    case cl: TypedClass => encodeTypedClass(cl)
  }

  implicit val typingResultEncoder: Encoder.AsObject[TypingResult] = Encoder.AsObject.instance(encodeTypingResult)

  implicit val simpleValEncoder: Encoder[AdditionalDataValue] = new Encoder[AdditionalDataValue] {

    override def apply(a: AdditionalDataValue): Json = a match {
      case StringValue(value)  => fromString(value)
      case LongValue(value)    => fromLong(value)
      case BooleanValue(value) => fromBoolean(value)
    }

  }

}
