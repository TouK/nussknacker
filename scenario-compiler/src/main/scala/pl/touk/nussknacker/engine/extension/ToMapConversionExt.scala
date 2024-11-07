package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler

import java.lang.{Boolean => JBoolean}
import java.util.{Collection => JCollection, HashMap => JHashMap, Map => JMap, Set => JSet}

object ToMapConversionExt extends ToCollectionConversion[JMap[_, _]] with ConversionExt {
  private val booleanTyping    = Typed.typedClass[Boolean]
  private val mapTyping        = Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown))
  private val keyName          = "key"
  private val valueName        = "value"
  private val keyAndValueNames = JSet.of(keyName, valueName)

  private val isMapMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToMapConversionExt.typingFunction(invocationTarget).map(_ => booleanTyping),
    signature = MethodTypeInfo.noArgTypeInfo(booleanTyping),
    name = "isMap",
    description = Some("Check whether can be convert to a map")
  )

  private val toMapDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToMapConversionExt.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(mapTyping),
    name = "toMap",
    description = Option("Convert to a map or throw exception in case of failure")
  )

  private val toMapOrNullDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToMapConversionExt.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(mapTyping),
    name = "toMapOrNull",
    description = Option("Convert to a map or null in case of failure")
  )

  override def definitions(): List[MethodDefinition] = List(
    isMapMethodDefinition,
    toMapDefinition,
    toMapOrNullDefinition,
  )

  override val typingResult: TypingResult = Typed.genericTypeClass(resultTypeClass, List(Unknown, Unknown))
  override val conversion: Conversion[_]  = this

  override val typingFunction: TypingResult => ValidatedNel[GenericFunctionTypingError, TypingResult] =
    invocationTarget =>
      invocationTarget.withoutValue match {
        case TypedClass(_, List(TypedObjectTypingResult(fields, _, _)))
            if fields.contains(keyName) && fields.contains(valueName) =>
          val params = List(fields.get(keyName), fields.get(valueName)).flatten
          Typed.genericTypeClass[JMap[_, _]](params).validNel
        case TypedClass(_, List(TypedObjectTypingResult(_, _, _))) =>
          GenericFunctionTypingError.OtherError("List element must contain 'key' and 'value' fields").invalidNel
        case Unknown => Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown)).validNel
        case _       => GenericFunctionTypingError.ArgumentTypeError.invalidNel
      }

  override def canConvert(value: Any): JBoolean = value match {
    case _: JMap[_, _]     => true
    case c: JCollection[_] => canConvertToMap(c)
    case a: Array[_]       => canConvertToMap(ConversionHandler.convertArrayToList(a))
    case _                 => false
  }

  override def convertEither(value: Any): Either[Throwable, JMap[_, _]] =
    value match {
      case m: JMap[_, _] => Right(m)
      case a: Array[_]   => convertEither(ConversionHandler.convertArrayToList(a))
      case c: JCollection[JMap[_, _] @unchecked] if canConvertToMap(c) =>
        val map = new JHashMap[Any, Any]()
        c.forEach(e => map.put(e.get(keyName), e.get(valueName)))
        Right(map)
      case x => Left(new IllegalArgumentException(s"Cannot convert: $x to a Map"))
    }

  private def canConvertToMap(c: JCollection[_]): Boolean = c.isEmpty || c
    .stream()
    .allMatch {
      case m: JMap[_, _] if !m.isEmpty => m.keySet().containsAll(keyAndValueNames)
      case _                           => false
    }

}
