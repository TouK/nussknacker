package pl.touk.nussknacker.engine.api.typed

import cats.data.NonEmptyList
import io.circe._
import io.circe.Json._
import pl.touk.nussknacker.engine.api.typed.TypeEncoders.typeField
import pl.touk.nussknacker.engine.api.typed.TypingType.{decoder, TypingType}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

//TODO: refactor way of encoding to easier handle decoding.

// If changes are made to Encoders/Decoders should also change Schemas in NodesApiEndpoints.TypingDtoSchemas for OpenApi
object TypeEncoders {

  private[typed] val typeField = "type"

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
      case typing.Unknown             => encodeUnknown
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
      val dataEncoded: (String, Json) = "value" -> ValueEncoder
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

/*
  We don't just pass classLoader here, as it won't handle e.g. primitives out of the box.
  Primitives can be handled by ClassUtils from spring, but we don't want to have explicit dependency in this module
  See NodeResources in UI for usage
 */
class TypingResultDecoder(loadClass: String => Class[_]) {

  implicit val decodeTypingResults: Decoder[TypingResult] = Decoder.instance { hcursor =>
    hcursor.downField(typeField).as[TypingType].flatMap {
      case TypingType.Unknown                 => Right(Unknown)
      case TypingType.TypedNull               => Right(TypedNull)
      case TypingType.TypedUnion              => typedUnion(hcursor)
      case TypingType.TypedDict               => typedDict(hcursor)
      case TypingType.TypedTaggedValue        => typedTaggedValue(hcursor)
      case TypingType.TypedObjectWithValue    => typedObjectWithValue(hcursor)
      case TypingType.TypedObjectTypingResult => typedObjectTypingResult(hcursor)
      case TypingType.TypedClass              => typedClass(hcursor)
    }
  }

  private implicit val additionalDataValueDecoder: Decoder[AdditionalDataValue] = {
    Decoder.decodeLong
      .map[AdditionalDataValue](LongValue)
      .or(Decoder.decodeString.map[AdditionalDataValue](StringValue))
      .or(Decoder.decodeBoolean.map[AdditionalDataValue](BooleanValue))
      .or(Decoder.failedWithMessage("Cannot convert to AdditionalDataValue"))
  }

  private implicit val singleTypingResult: Decoder[SingleTypingResult] = decodeTypingResults.emap {
    case e: SingleTypingResult => Right(e)
    case e                     => Left(s"$e is not SingleTypingResult")
  }

  private def typedTaggedValue(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj)
    tag        <- obj.downField("tag").as[String]
  } yield TypedTaggedValue(valueClass, tag)

  private def typedObjectWithValue(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj)
    value      <- ValueDecoder.decodeValue(valueClass, obj.downField("value"))
  } yield TypedObjectWithValue(valueClass, value)

  private def typedObjectTypingResult(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj)
    fields     <- obj.downField("fields").as[ListMap[String, TypingResult]]
    additional <- obj
      .downField("additionalInfo")
      .as[Option[Map[String, AdditionalDataValue]]]
      .map(_.getOrElse(Map.empty))
  } yield Typed.record(fields, valueClass, additional)

  private def typedDict(obj: HCursor): Decoder.Result[TypingResult] = {
    val dict = obj.downField("dict")
    for {
      id        <- dict.downField("id").as[String]
      valueType <- dict.downField("valueType").as[SingleTypingResult]
    } yield TypedDict(id, valueType)
  }

  // This implementation is lax. We are not warranting that the result will be a TypedUnion. We use Typed.apply
  // under the hood which can e.g. produce a single element for a NEL.one. In general, it can produce the other
  // number of elements than it is passed. We could be strict but we decided that being lax gives more benefits
  // than risks
  private def typedUnion(obj: HCursor): Decoder.Result[TypingResult] = {
    obj.downField("union").as[List[SingleTypingResult]].flatMap { list =>
      NonEmptyList
        .fromList(list)
        .map(nel => Right(Typed(nel)))
        .getOrElse(
          Left(DecodingFailure(s"Union should has at least 2 elements but it has ${list.size} elements", obj.history))
        )
    }
  }

  private def typedClass(obj: HCursor): Decoder.Result[TypedClass] = {
    for {
      refClazzName <- obj.downField("refClazzName").as[String]
      clazz        <- tryToLoadClass(refClazzName, obj)
      params       <- obj.downField("params").as[List[TypingResult]]
    } yield Typed.genericTypeClass(clazz, params)
  }

  private def tryToLoadClass(name: String, obj: HCursor): Decoder.Result[Class[_]] = {
    if (name == Typed.KlassForArrays.getName) {
      // loading of array class causes Failed to load class [Ljava.lang.Object; with [Ljava.lang.Object;
      Right(Typed.KlassForArrays)
    } else {
      Try(loadClass(name)) match {
        case Success(value) => Right(value)
        case Failure(thr)   => Left(DecodingFailure(s"Failed to load class $name with ${thr.getMessage}", obj.history))
      }
    }
  }

}

object TypingType extends Enumeration {

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
