package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean}
import java.util.{ArrayList => JArrayList, Collection => JCollection, List => JList}

class ToListConversionExt(target: Any) {

  def isList(): Boolean        = ToListConversionExt.canConvert(target)
  def toList(): JList[_]       = ToListConversionExt.convert(target)
  def toListOrNull(): JList[_] = ToListConversionExt.convertOrNull(target)

}

object ToListConversionExt extends ConversionExt with ToCollectionConversion {
  private val booleanTyping   = Typed.typedClass[Boolean]
  private val listTyping      = Typed.genericTypeClass[JList[_]](List(Unknown))
  private val collectionClass = classOf[JCollection[_]]

  private val isListMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (targetTyping, _) => ToListConversionExt.typingFunction(targetTyping).map(_ => booleanTyping),
    signature = MethodTypeInfo.noArgTypeInfo(booleanTyping),
    name = "isList",
    description = Some("Check whether can be convert to a list")
  )

  private val toListDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToListConversionExt.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(listTyping),
    name = "toList",
    description = Option("Convert to a list or throw exception in case of failure")
  )

  private val toListOrNullDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToListConversionExt.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(listTyping),
    name = "toListOrNull",
    description = Option("Convert to a list or null in case of failure")
  )

  override val definitions: List[MethodDefinition] = List(
    isListMethodDefinition,
    toListDefinition,
    toListOrNullDefinition,
  )

  override type ExtensionMethodInvocationTarget = ToListConversionExt
  override val invocationTargetClass: Class[ToListConversionExt] = classOf[ToListConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToListConversionExt] =
    (target: Any) => new ToListConversionExt(target)

  override type ResultType = JList[_]
  override val resultTypeClass: Class[JList[_]] = classOf[JList[_]]
  override def typingResult: TypingResult       = Typed.genericTypeClass(resultTypeClass, List(Unknown))

  override def canConvert(value: Any): JBoolean = value.getClass.isAOrChildOf(collectionClass) || value.getClass.isArray

  override def typingFunction(invocationTarget: TypingResult): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    invocationTarget.withoutValue match {
      case TypedClass(klass, params) if klass.isAOrChildOf(collectionClass) || klass.isArray =>
        Typed.genericTypeClass[JList[_]](params).validNel
      case Unknown => Typed.genericTypeClass[JList[_]](List(Unknown)).validNel
      case _       => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  override def convertEither(value: Any): Either[Throwable, JList[_]] = {
    value match {
      case l: JList[_]       => Right(l)
      case c: JCollection[_] => Right(new JArrayList[Any](c))
      case a: Array[_]       => Right(ConversionHandler.convertArrayToList(a))
      case x                 => Left(new IllegalArgumentException(s"Cannot convert: $x to a List"))
    }
  }

}
