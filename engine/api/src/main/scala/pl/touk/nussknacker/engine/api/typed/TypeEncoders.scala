package pl.touk.nussknacker.engine.api.typed

import io.circe.Json._
import io.circe._
import pl.touk.nussknacker.engine.api.typed.TypeEncoders.typeField
import pl.touk.nussknacker.engine.api.typed.TypingType.TypingType
import pl.touk.nussknacker.engine.api.typed.typing._

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

  private def encodeTypingResult(result: TypingResult): Json = fromJsonObject((result match {
    case single: SingleTypingResult => encodeSingleTypingResult(single)
    case typing.Unknown => encodeUnknown
    case TypedUnion(classes) =>
      JsonObject("union" -> fromValues(classes.map(encodeTypingResult).toList))
  }).+:(typeField -> fromString(TypingType.forType(result).toString)))

  private def encodeSingleTypingResult(result: SingleTypingResult): JsonObject = result match {
    case TypedObjectTypingResult(fields, objType) =>
      val objTypeEncoded = encodeTypedClass(objType)
      val fieldsEncoded = "fields" -> fromFields(fields.mapValues(encodeTypingResult).toList)
      objTypeEncoded.+:(fieldsEncoded)
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
    fields <- obj.downField("fields").as[Map[String, TypingResult]].right
  } yield TypedObjectTypingResult(fields, valueClass)

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
    } yield TypedClass(clazz, params)
  }

  private def tryToLoadClass(name: String, obj: HCursor): Decoder.Result[Class[_]] = {
    Try(loadClass(name)) match {
      case Success(value) => Right(value)
      case Failure(thr) => Left(DecodingFailure(s"Failed to load class $name with ${thr.getMessage}", obj.history))
    }
  }


}

object TypingType extends Enumeration {

  implicit val decoder: Decoder[TypingType.Value] = Decoder.enumDecoder(TypingType)

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

