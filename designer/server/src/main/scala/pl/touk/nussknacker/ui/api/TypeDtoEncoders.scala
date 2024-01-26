package pl.touk.nussknacker.ui.api

import io.circe._
import io.circe.Json._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.AdditionalDataValue
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.api.TypeDtoEncoders.typeField
import pl.touk.nussknacker.ui.api.TypingTypeDto.TypingType
import pl.touk.nussknacker.ui.api.typingDtoSchemas._

object TypeDtoEncoders {

  val typeField = "type"

  private def encodeTypedClassDto(ref: TypedClassDto): JsonObject = JsonObject(
    "refClazzName" -> fromString(ref.klass.getName),
    "params"       -> fromValues(ref.params.map(typ => fromJsonObject(encodeTypingResultDto(typ))))
  )

  private val encodeUnknown =
    JsonObject("refClazzName" -> fromString(classOf[Object].getName), "params" -> fromValues(Nil))

  private val encodeNull =
    JsonObject("refClazzName" -> fromString(classOf[Object].getName), "params" -> fromValues(Nil))

  private def encodeTypingResultDto(dto: TypingResultDto): JsonObject =
    (dto match {
      case single: SingleTypingResultDto => encodeSingleTypingResult(single)
      case typingDtoSchemas.UnknownDto   => encodeUnknown
      case typingDtoSchemas.TypedNullDto => encodeNull
      case TypedUnionDto(classes) =>
        JsonObject("union" -> fromValues(classes.map(typ => fromJsonObject(encodeTypingResultDto(typ))).toList))
    })
      .add(typeField, fromString(TypingTypeDto.forType(dto).toString))
      .add("display", fromString(dto.display))

  private def encodeSingleTypingResult(dto: SingleTypingResultDto): JsonObject =
    dto match {
      case TypedObjectTypingResultDto(fields, objType, additionalInfo) =>
        encodeTypedObjectTypingResultDto(fields, objType, additionalInfo)
      case TypedDictDto(dictId, valueType) =>
        encodeTypedDictDto(dictId, valueType)
      case TypedTaggedValueDto(underlying, tag) =>
        encodeTypedTaggedValueDto(underlying, tag)
      case TypedObjectWithValueDto(underlying, _, _, valueInJson) =>
        encodeTypedObjectWithValueDto(underlying, valueInJson)
      case clDto: TypedClassDto => encodeTypedClassDto(clDto)
    }

  private def encodeTypedObjectWithValueDto(underlying: TypedClassDto, valueInJson: Json): JsonObject = {
    val objTypeEncoded              = encodeTypingResultDto(underlying)
    val dataEncoded: (String, Json) = "value" -> valueInJson
    objTypeEncoded.+:(dataEncoded)
  }

  private def encodeTypedTaggedValueDto(underlying: SingleTypingResultDto, tag: String): JsonObject = {
    val objTypEncoded = encodeTypingResultDto(underlying)
    val tagEncoded    = "tag" -> fromString(tag)
    objTypEncoded.+:(tagEncoded)
  }

  private def encodeTypedDictDto(dictId: String, valueType: SingleTypingResultDto): JsonObject = {
    JsonObject(
      "dict" -> obj(
        "id"        -> fromString(dictId),
        "valueType" -> fromJsonObject(encodeTypingResultDto(valueType))
      )
    )
  }

  private def encodeTypedObjectTypingResultDto(
      fields: Map[String, TypingResultDto],
      objType: TypedClassDto,
      additionalInfo: Map[String, AdditionalDataValue]
  ): JsonObject = {
    val objTypeEncoded = encodeTypedClassDto(objType)
    val fieldsEncoded =
      "fields" -> fromFields(fields.mapValuesNow(typ => fromJsonObject(encodeTypingResultDto(typ))).toList)
    val standardFields = objTypeEncoded.+:(fieldsEncoded)
    if (additionalInfo.isEmpty) {
      standardFields
    } else {
      standardFields.+:(
        "additionalInfo" -> implicitly[Encoder[Map[String, AdditionalDataValue]]].apply(additionalInfo)
      )
    }
  }

  implicit val typingResultEncoder: Encoder.AsObject[TypingResultDto] = Encoder.AsObject.instance(encodeTypingResultDto)

  implicit val simpleValEncoder: Encoder[AdditionalDataValue] = new Encoder[AdditionalDataValue] {

    override def apply(a: AdditionalDataValue): Json = a match {
      case typing.StringValue(value)  => fromString(value)
      case typing.LongValue(value)    => fromLong(value)
      case typing.BooleanValue(value) => fromBoolean(value)
    }

  }

}

class TypingResultDtoDecoder {

  implicit val decodeTypingResultDto: Decoder[TypingResultDto] = Decoder.instance { hcursor =>
    hcursor.downField(typeField).as[TypingType].flatMap {
      case TypingTypeDto.Unknown                 => Right(UnknownDto)
      case TypingTypeDto.TypedNull               => Right(TypedNullDto)
      case TypingTypeDto.TypedUnion              => typedUnionDto(hcursor)
      case TypingTypeDto.TypedDict               => typedDictDto(hcursor)
      case TypingTypeDto.TypedTaggedValue        => typedTaggedValueDto(hcursor)
      case TypingTypeDto.TypedObjectWithValue    => typedObjectWithValueDto(hcursor)
      case TypingTypeDto.TypedObjectTypingResult => typedObjectTypingResultDto(hcursor)
      case TypingTypeDto.TypedClass              => typedClassDto(hcursor)
    }
  }

  private implicit val additionalDataValueDecoder: Decoder[AdditionalDataValue] = {
    Decoder.decodeLong
      .map[AdditionalDataValue](typing.LongValue)
      .or(Decoder.decodeString.map[AdditionalDataValue](typing.StringValue))
      .or(Decoder.decodeBoolean.map[AdditionalDataValue](typing.BooleanValue))
      .or(Decoder.failedWithMessage("Cannot convert to AdditionalDataValue"))
  }

  private implicit val singleTypingResultDto: Decoder[SingleTypingResultDto] = decodeTypingResultDto.emap {
    case e: SingleTypingResultDto => Right(e)
    case e                        => Left(s"$e is not SingleTypingResult")
  }

  private def typedTaggedValueDto(obj: HCursor): Decoder.Result[TypingResultDto] = for {
    valueClass <- typedClassDto(obj)
    tag        <- obj.downField("tag").as[String]
  } yield TypedTaggedValueDto(valueClass, tag)

  private def typedObjectWithValueDto(obj: HCursor): Decoder.Result[TypingResultDto] = for {
    valueClass <- typedClassDto(obj)
    value      <- obj.downField("value").as[Json]
  } yield TypedObjectWithValueDto(valueClass, value, Some(valueClass.classOnWait), value)

  private def typedObjectTypingResultDto(obj: HCursor): Decoder.Result[TypingResultDto] = for {
    valueClass <- typedClassDto(obj)
    fields     <- obj.downField("fields").as[Map[String, TypingResultDto]]
    additional <- obj
      .downField("additionalInfo")
      .as[Option[Map[String, AdditionalDataValue]]]
      .map(_.getOrElse(Map.empty))
  } yield TypedObjectTypingResultDto(fields, valueClass, additional)

  private def typedDictDto(obj: HCursor): Decoder.Result[TypingResultDto] = {
    val dict = obj.downField("dict")
    for {
      id        <- dict.downField("id").as[String]
      valueType <- dict.downField("valueType").as[SingleTypingResultDto]
    } yield TypedDictDto(id, valueType)
  }

  private def typedUnionDto(obj: HCursor): Decoder.Result[TypingResultDto] = {
    obj.downField("union").as[Set[SingleTypingResultDto]].map(set => TypedUnionDto(set))
  }

  private def typedClassDto(obj: HCursor): Decoder.Result[TypedClassDto] = {
    for {
      refClazzName <- obj.downField("refClazzName").as[String]
      params       <- obj.downField("params").as[List[TypingResultDto]]
      paramsOnWait <- obj.downField("params").as[Json]
    } yield TypedClassDto("".getClass, params, refClazzName, paramsOnWait)
  }

}

object TypingResultDtoDecoder

object TypingTypeDto extends Enumeration {

  implicit val decoder: Decoder[TypingTypeDto.Value] = Decoder.decodeEnumeration(TypingTypeDto)

  type TypingType = Value

  val TypedUnion, TypedDict, TypedObjectTypingResult, TypedTaggedValue, TypedClass, TypedObjectWithValue, TypedNull,
      Unknown = Value

  def forType(typingResultDto: TypingResultDto): TypingTypeDto.Value = typingResultDto match {
    case _: TypedClassDto              => TypedClass
    case _: TypedUnionDto              => TypedUnion
    case _: TypedDictDto               => TypedDict
    case _: TypedObjectTypingResultDto => TypedObjectTypingResult
    case _: TypedTaggedValueDto        => TypedTaggedValue
    case _: TypedObjectWithValueDto    => TypedObjectWithValue
    case typingDtoSchemas.TypedNullDto => TypedNull
    case typingDtoSchemas.UnknownDto   => Unknown
  }

}
