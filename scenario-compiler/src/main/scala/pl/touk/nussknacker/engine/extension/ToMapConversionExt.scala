package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler

import java.lang.{Boolean => JBoolean}
import java.util.{Collection => JCollection, HashMap => JHashMap, Map => JMap, Set => JSet}

class ToMapConversionExt(target: Any) {

  def isMap(): Boolean          = ToMapConversionExt.canConvert(target)
  def toMap(): JMap[_, _]       = ToMapConversionExt.convert(target)
  def toMapOrNull(): JMap[_, _] = ToMapConversionExt.convertOrNull(target)

}

object ToMapConversionExt extends ExtensionMethodsHandler with CollectionConversion {
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

  private val definitions = List(
    isMapMethodDefinition,
    toMapDefinition,
    toMapOrNullDefinition,
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = ToMapConversionExt
  override val invocationTargetClass: Class[ToMapConversionExt] = classOf[ToMapConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToMapConversionExt] =
    (target: Any) => new ToMapConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (appliesToConversion(clazz)) definitions
    else Map.empty

  // Conversion extension should be available for every class in a runtime
  override def appliesToClassInRuntime(clazz: Class[_]): Boolean =
    true

  override type ResultType = JMap[_, _]
  override val resultTypeClass: Class[JMap[_, _]] = classOf[ResultType]
  override def typingResult: TypingResult         = Typed.genericTypeClass(resultTypeClass, List(Unknown, Unknown))

  override def canConvert(value: Any): JBoolean = value match {
    case _: JMap[_, _]     => true
    case c: JCollection[_] => canConvertToMap(c)
    case a: Array[_]       => canConvertToMap(ConversionHandler.convertArrayToList(a))
    case _                 => false
  }

  override def typingFunction(invocationTarget: TypingResult): ValidatedNel[GenericFunctionTypingError, TypingResult] =
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
