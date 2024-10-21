package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ListConversion.collectionClass
import pl.touk.nussknacker.engine.extension.MapConversion.{keyName, valueName}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.util.{List => JList, Map => JMap}

class CollectionConversionExt(target: Any) {

  def isList(): Boolean         = ListConversion.canConvert(target)
  def toList(): JList[_]        = ListConversion.convert(target)
  def toListOrNull(): JList[_]  = ListConversion.convertOrNull(target)
  def isMap(): Boolean          = MapConversion.canConvert(target)
  def toMap(): JMap[_, _]       = MapConversion.convert(target)
  def toMapOrNull(): JMap[_, _] = MapConversion.convertOrNull(target)

}

object CollectionConversionExt extends ExtensionMethodsHandler {
  private val unknownClass  = classOf[Object]
  private val booleanTyping = Typed.typedClass[Boolean]

  private val toMapDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toMapTypeFunction(invocationTarget),
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = Unknown
    ),
    name = "toMap",
    description = Option("Convert to a map or throw exception in case of failure")
  )

  private val toListDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toListTypeFunction(invocationTarget),
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = Unknown
    ),
    name = "toList",
    description = Option("Convert to a list or throw exception in case of failure")
  )

  private val isMapMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toMapTypeFunction(invocationTarget).map(_ => booleanTyping),
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = Unknown
    ),
    name = "isMap",
    description = Some("Check whether can be convert to a map")
  )

  private val isListMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => toListTypeFunction(invocationTarget).map(_ => booleanTyping),
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = Unknown
    ),
    name = "isList",
    description = Some("Check whether can be convert to a list")
  )

  private val definitions = List(
    isMapMethodDefinition,
    toMapDefinition,
    toMapDefinition.copy(name = "toMapOrNull", description = Some("Convert to a map or null in case of failure")),
    isListMethodDefinition,
    toListDefinition,
    toListDefinition.copy(name = "toListOrNull", description = Some("Convert to a list or null in case of failure")),
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = CollectionConversionExt
  override val invocationTargetClass: Class[CollectionConversionExt] = classOf[CollectionConversionExt]

  override def createConverter(): ToExtensionMethodInvocationTargetConverter[CollectionConversionExt] =
    (target: Any) => new CollectionConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (clazz.isAOrChildOf(collectionClass) || clazz == unknownClass || clazz.isArray) definitions
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
