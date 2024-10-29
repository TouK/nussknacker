package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ToMapConversion.{keyName, valueName}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.util.{Collection => JCollection, List => JList, Map => JMap}

class CollectionConversionExt(target: Any) {

  def isList(): Boolean         = ToListConversion.canConvert(target)
  def toList(): JList[_]        = ToListConversion.convert(target)
  def toListOrNull(): JList[_]  = ToListConversion.convertOrNull(target)
  def isMap(): Boolean          = ToMapConversion.canConvert(target)
  def toMap(): JMap[_, _]       = ToMapConversion.convert(target)
  def toMapOrNull(): JMap[_, _] = ToMapConversion.convertOrNull(target)

}

object CollectionConversionExt extends ExtensionMethodsHandler {
  private val booleanTyping   = Typed.typedClass[Boolean]
  private val mapTyping       = Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown))
  private val listTyping      = Typed.genericTypeClass[JList[_]](List(Unknown))
  private val collectionClass = classOf[JCollection[_]]

  private val isMapMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toMapTypeFunction(invocationTarget).map(_ => booleanTyping),
    signature = MethodTypeInfo.noArgTypeInfo(booleanTyping),
    name = "isMap",
    description = Some("Check whether can be convert to a map")
  )

  private val toMapDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toMapTypeFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(mapTyping),
    name = "toMap",
    description = Option("Convert to a map or throw exception in case of failure")
  )

  private val toMapOrNullDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toMapTypeFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(mapTyping),
    name = "toMapOrNull",
    description = Option("Convert to a map or null in case of failure")
  )

  private val isListMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toListTypeFunction(invocationTarget).map(_ => booleanTyping),
    signature = MethodTypeInfo.noArgTypeInfo(booleanTyping),
    name = "isList",
    description = Some("Check whether can be convert to a list")
  )

  private val toListDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toListTypeFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(listTyping),
    name = "toList",
    description = Option("Convert to a list or throw exception in case of failure")
  )

  private val toListOrNullDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toListTypeFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(listTyping),
    name = "toListOrNull",
    description = Option("Convert to a list or null in case of failure")
  )

  private val definitions = List(
    isMapMethodDefinition,
    toMapDefinition,
    toMapOrNullDefinition,
    isListMethodDefinition,
    toListDefinition,
    toListOrNullDefinition,
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = CollectionConversionExt
  override val invocationTargetClass: Class[CollectionConversionExt] = classOf[CollectionConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[CollectionConversionExt] =
    (target: Any) => new CollectionConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (ToListConversion.applies(clazz) || ToMapConversion.applies(clazz)) definitions
    else Map.empty

  // Conversion extension should be available for every class in a runtime
  override def applies(clazz: Class[_]): Boolean =
    true

  private def toMapTypeFunction(
      invocationTarget: TypingResult
  ): ValidatedNel[GenericFunctionTypingError, TypingResult] =
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

  private def toListTypeFunction(
      invocationTarget: TypingResult
  ): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    invocationTarget.withoutValue match {
      case TypedClass(klass, params) if klass.isAOrChildOf(collectionClass) || klass.isArray =>
        Typed.genericTypeClass[JList[_]](params).validNel
      case Unknown => Typed.genericTypeClass[JList[_]](List(Unknown)).validNel
      case _       => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

}
