package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.clazz.{FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastOrConversionExt.{canBeMethodName, orNullSuffix, toMethodName}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean}
import java.util.{Collection => JCollection, HashMap => JHashMap, Map => JMap, Set => JSet}
import scala.annotation.tailrec

object ToMapConversionExt extends ConversionExt(ToMapConversion) {
  private val booleanTyping = Typed.typedClass[Boolean]
  private val mapTyping     = Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown))

  private val isMapMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToMapConversion.typingFunction(invocationTarget).map(_ => booleanTyping),
    signature = MethodTypeInfo.noArgTypeInfo(booleanTyping),
    name = s"${canBeMethodName}Map",
    description = Some("Check whether can be convert to a map")
  )

  private val toMapDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToMapConversion.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(mapTyping),
    name = s"${toMethodName}Map",
    description = Option("Convert to a map or throw exception in case of failure")
  )

  private val toMapOrNullDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToMapConversion.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(mapTyping),
    name = s"${toMethodName}Map${orNullSuffix}",
    description = Option("Convert to a map or null in case of failure")
  )

  override protected def definitions(): List[MethodDefinition] = List(
    isMapMethodDefinition,
    toMapDefinition,
    toMapOrNullDefinition,
  )

}

object ToMapConversion extends Conversion[JMap[_, _]] {

  private val mapClass = classOf[JMap[_, _]]

  private val collectionClass = classOf[JCollection[_]]

  private[extension] val keyName   = "key"
  private[extension] val valueName = "value"
  private val keyAndValueNames     = JSet.of(keyName, valueName)

  override val typingResult: TypingResult = Typed.genericTypeClass(resultTypeClass, List(Unknown, Unknown))

  override val typingFunction: TypingResult => ValidatedNel[GenericFunctionTypingError, TypingResult] =
    invocationTarget =>
      invocationTarget.withoutValue match {
        case TypedClass(_, List(TypedObjectTypingResult(fields, _, _)))
            if fields.contains(keyName) && fields.contains(valueName) =>
          val params = List(fields.get(keyName), fields.get(valueName)).flatten
          Typed.genericTypeClass[JMap[_, _]](params).validNel
        case TypedClass(_, List(TypedObjectTypingResult(_, _, _))) =>
          GenericFunctionTypingError.OtherError("List element must contain 'key' and 'value' fields").invalidNel
        case TypedClass(_, List(TypedClass(klass, _))) if klass.isAOrChildOf(mapClass) =>
          Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown)).validNel
        case TypedClass(_, List(Unknown)) => Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown)).validNel
        case Unknown                      => Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown)).validNel
        case _                            => GenericFunctionTypingError.ArgumentTypeError.invalidNel
      }

  @tailrec
  override def convertEither(value: Any): Either[Throwable, JMap[_, _]] =
    value match {
      case m: JMap[_, _]                           => Right(m)
      case a: Array[_]                             => convertEither(ConversionHandler.convertArrayToList(a))
      case c: JCollection[_] if canConvertToMap(c) => Right(convertToMap(c))
      case x                                       => Left(new IllegalArgumentException(s"Cannot convert: $x to a Map"))
    }

  // We could leave underlying method using convertEither as well but this implementation is faster
  override def canConvert(value: Any): JBoolean = value match {
    case _: JMap[_, _]     => true
    case c: JCollection[_] => canConvertToMap(c)
    case a: Array[_]       => canConvertToMap(ConversionHandler.convertArrayToList(a))
    case _                 => false
  }

  private def canConvertToMap(c: JCollection[_]): Boolean = c
    .stream()
    .allMatch {
      case m: JMap[_, _]                => m.keySet().containsAll(keyAndValueNames)
      case _: JMap.Entry[_, _] => true
      case _                            => false
    }

  private def convertToMap(c: JCollection[_]): JMap[_, _] = {
    val map = new JHashMap[Any, Any]()
    c.forEach {
      case e: JMap[_, _]                => map.put(e.get(keyName), e.get(valueName))
      case e: JMap.Entry[_, _] => map.put(e.getKey, e.getValue)
    }
    map
  }

  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(collectionClass) || clazz == unknownClass || clazz.isArray)

}
