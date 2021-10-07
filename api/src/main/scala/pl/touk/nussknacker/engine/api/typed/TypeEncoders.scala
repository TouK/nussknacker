package pl.touk.nussknacker.engine.api.typed

import io.circe.Json._
import io.circe._
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.TypeEncoders.typeField
import pl.touk.nussknacker.engine.api.typed.TypingType.{TypingType, decoder}
import pl.touk.nussknacker.engine.api.typed.typing._

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

//TODO: refactor way of encoding to easier handle decoding.
object TypeEncoders {

  private[typed] val typeField = "type"

  private def encodeTypedClass(ref: TypedClass): JsonObject = JsonObject(
    "refClazzName" -> fromString(ref.klass.getName),
    "params" -> fromValues(ref.params.map(encodeTypingResult))
  )

  private val encodeUnknown = JsonObject("refClazzName" -> fromString(classOf[Object].getName),
    "params" -> fromValues(Nil))

  private def encodeTypingResult(result: TypingResult): Json = fromJsonObject(
    (result match {
      case single: SingleTypingResult => encodeSingleTypingResult(single)
      case typing.Unknown => encodeUnknown
      case TypedUnion(classes) =>
        JsonObject("union" -> fromValues(classes.map(encodeTypingResult).toList))
    })
      .+:(typeField -> fromString(TypingType.forType(result).toString))
      .+:("display" -> fromString(result.display)))

  private def encodeSingleTypingResult(result: SingleTypingResult): JsonObject = result match {
    case TypedObjectTypingResult(fields, objType, additionalInfo) =>
      val objTypeEncoded = encodeTypedClass(objType)
      val fieldsEncoded = "fields" -> fromFields(fields.mapValues(encodeTypingResult).toList)
      val standardFields = objTypeEncoded.+:(fieldsEncoded)
      if (additionalInfo.isEmpty) {
        standardFields
      } else {
        standardFields.+:("additionalInfo" -> implicitly[Encoder[Map[String, AdditionalDataValue]]].apply(additionalInfo))
      }
    case dict: TypedDict =>
      JsonObject("dict" -> obj(
        "id" -> fromString(dict.dictId),
        "valueType" -> encodeTypingResult(dict.valueType)))
    case TypedTaggedValue(underlying, tag) =>
      val objTypeEncoded = encodeTypingResult(underlying).asObject.getOrElse(JsonObject.empty)
      val tagEncoded = "tag" -> fromString(tag)
      objTypeEncoded.+:(tagEncoded)
    case cl: TypedClass => encodeTypedClass(cl)
  }

  implicit val typingResultEncoder: Encoder[TypingResult] = Encoder.instance(encodeTypingResult)

  implicit val simpleValEncoder: Encoder[AdditionalDataValue] = new Encoder[AdditionalDataValue] {
    override def apply(a: AdditionalDataValue): Json = a match {
      case StringValue(value) => fromString(value)
      case LongValue(value) => fromLong(value)
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
    hcursor.downField(typeField).as[TypingType].right.flatMap {
      case TypingType.Unknown => Right(Unknown)
      case TypingType.TypedUnion => typedUnion(hcursor)
      case TypingType.TypedDict => typedDict(hcursor)
      case TypingType.TypedTaggedValue => typedTaggedValue(hcursor)
      case TypingType.TypedObjectTypingResult => typedObjectTypingResult(hcursor)
      case TypingType.TypedClass => typedClass(hcursor)
    }
  }

  private implicit val additionalDataValueDecoder: Decoder[AdditionalDataValue] = {
    Decoder.decodeLong.map[AdditionalDataValue](LongValue)
      .or(Decoder.decodeString.map[AdditionalDataValue](StringValue))
      .or(Decoder.decodeBoolean.map[AdditionalDataValue](BooleanValue))
      .or(Decoder.failedWithMessage("Cannot convert to AdditionalDataValue"))
  }

  private implicit val singleTypingResult: Decoder[SingleTypingResult] = decodeTypingResults.emap {
    case e:SingleTypingResult => Right(e)
    case e => Left(s"$e is not SingleTypingResult")
  }

  private def typedTaggedValue(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj).right
    tag <- obj.downField("tag").as[String].right
  } yield TypedTaggedValue(valueClass, tag)

  private def typedObjectTypingResult(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj).right
    fields <- obj.downField("fields").as[ListMap[String, TypingResult]].right
    additional <- obj.downField("additionalInfo").as[Option[Map[String, AdditionalDataValue]]].right.map(_.getOrElse(Map.empty)).right
  } yield TypedObjectTypingResult(fields, valueClass, additional)

  private def typedDict(obj: HCursor): Decoder.Result[TypingResult] = {
    val dict = obj.downField("dict")
    for {
      id <- dict.downField("id").as[String].right
      valueType <- dict.downField("valueType").as[SingleTypingResult].right
    } yield TypedDict(id, valueType)
  }

  private def typedUnion(obj: HCursor): Decoder.Result[TypingResult] = {
    obj.downField("union").as[Set[SingleTypingResult]].right.map(TypedUnion)
  }

  private def typedClass(obj: HCursor): Decoder.Result[TypedClass] = {
    for {
      refClazzName <- obj.downField("refClazzName").as[String].right
      clazz <- tryToLoadClass(refClazzName, obj).right
      params <- obj.downField("params").as[List[TypingResult]].right
    } yield Typed.typedClass(clazz, params)
  }

  private def tryToLoadClass(name: String, obj: HCursor): Decoder.Result[Class[_]] = {
    Try(loadClass(name)) match {
      case Success(value) => Right(value)
      case Failure(thr) => Left(DecodingFailure(s"Failed to load class $name with ${thr.getMessage}", obj.history))
    }
  }


}

object TypingType extends Enumeration {

  implicit val decoder: Decoder[TypingType.Value] = Decoder.decodeEnumeration(TypingType)

  type TypingType = Value

  val TypedUnion, TypedDict, TypedObjectTypingResult, TypedTaggedValue, TypedClass, Unknown = Value

  def forType(typingResult: TypingResult): TypingType.Value = typingResult match {
    case _: TypedClass => TypedClass
    case _: TypedUnion => TypedUnion
    case _: TypedDict => TypedDict
    case _: TypedObjectTypingResult => TypedObjectTypingResult
    case _: TypedTaggedValue => TypedTaggedValue
    case typing.Unknown => Unknown
  }
}

