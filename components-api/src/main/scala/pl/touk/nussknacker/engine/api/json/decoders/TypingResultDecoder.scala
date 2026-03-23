package pl.touk.nussknacker.engine.api.json.decoders

import cats.data.NonEmptyList
import io.circe._
import pl.touk.nussknacker.engine.api.json.TypingType
import pl.touk.nussknacker.engine.api.json.TypingType.{typeField, TypingType}
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.typing.DisplayStrategy.{DefaultDisplayStrategy, JsonDisplayStrategy}

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

/*
  We don't just pass classLoader here, as it won't handle e.g. primitives out of the box.
  Primitives can be handled by ClassUtils from spring, but we don't want to have explicit dependency in this module
  See NodeResources in UI for usage
 */
class TypingResultDecoder(loadClass: String => Class[_]) {

  implicit val decodeTypingResults: Decoder[TypingResult] = Decoder.instance { hcursor =>
    hcursor.downField(typeField).as[TypingType].flatMap {
      case TypingType.Unknown                 => unknown(hcursor)
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

  private implicit val displayStrategyDecoder: Decoder[DisplayStrategy] = Decoder.decodeString.emap { displayStr =>
    DisplayStrategy.valueOfDisplay(displayStr).toRight(s"Unrecognized display value: '$displayStr'")
  }

  private def typedTaggedValue(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj)
    tag        <- obj.downField("tag").as[String]
  } yield TypedTaggedValue(valueClass, tag)

  private def typedObjectWithValue(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj)
    value      <- FromJsonTypingResultBasedDecoder.decodeValue(valueClass, obj.downField("value"))
  } yield TypedObjectWithValue(valueClass, value)

  private def typedObjectTypingResult(obj: HCursor): Decoder.Result[TypingResult] = for {
    valueClass <- typedClass(obj)
    fields     <- obj.downField("fields").as[ListMap[String, TypingResult]]
    additional <- obj
      .downField("additionalInfo")
      .as[Option[Map[String, AdditionalDataValue]]]
      .map(_.getOrElse(Map.empty))
  } yield Typed.record(fields, valueClass, additional)

  private def unknown(obj: HCursor): Decoder.Result[TypingResult] = {
    obj.downField("display") match {
      case displayAttribute if displayAttribute.succeeded =>
        displayAttribute.as[DisplayStrategy].map {
          case JsonDisplayStrategy    => Typed.json
          case DefaultDisplayStrategy => Unknown
        }
      case _ => Right(Unknown) // In case of no display attribute, return Unknown
    }
  }

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
    if (name == StandardTypesClasses.ArrayClass.getName) {
      // loading of array class causes Failed to load class [Ljava.lang.Object; with [Ljava.lang.Object;
      Right(StandardTypesClasses.ArrayClass)
    } else {
      Try(loadClass(name)) match {
        case Success(value) => Right(value)
        case Failure(thr)   => Left(DecodingFailure(s"Failed to load class $name with ${thr.getMessage}", obj.history))
      }
    }
  }

}
