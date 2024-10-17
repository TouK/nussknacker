package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CollectionConversionExt.{key, value}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.util.{ArrayList => JArrayList, Collection => JCollection, HashMap => JHashMap, List => JList, Map => JMap}

class CollectionConversionExt(target: Any) {

  def toList[T](): JList[T] = convertToList[T] match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def toMap[K, V](): JMap[K, V] = convertToMap[K, V] match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def toListOrNull[T](): JList[T] = convertToList[T] match {
    case Right(value) => value
    case Left(_)      => null
  }

  def toMapOrNull[K, V](): JMap[K, V] = convertToMap[K, V] match {
    case Right(value) => value
    case Left(_)      => null
  }

  private def convertToList[T]: Either[Throwable, JList[T]] = {
    target match {
      case l: JList[T @unchecked]       => Right(l)
      case c: JCollection[T @unchecked] => Right(new JArrayList[T](c))
      case x                            => Left(new IllegalArgumentException(s"Cannot convert $x to a List"))
    }
  }

  private def convertToMap[K, V]: Either[Throwable, JMap[K, V]] = {
    target match {
      case c: JCollection[JMap[String, Any] @unchecked] =>
        val map = new JHashMap[K, V]()
        c.forEach(e => map.put(e.get(key).asInstanceOf[K], e.get(value).asInstanceOf[V]))
        Right(map)
      case m: JMap[K, V] @unchecked => Right(m)
      case x                        => Left(new IllegalArgumentException(s"Cannot convert $x to a Map"))
    }
  }

}

object CollectionConversionExt extends ExtensionMethodsHandler {

  private val collectionClass = classOf[JCollection[_]]
  private val unknownClass    = classOf[Object]

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

  private val definitions = List(
    toMapDefinition,
    toMapDefinition.copy(name = "toMapOrNull", description = Some("Convert to a map or null in case of failure")),
    toListDefinition,
    toListDefinition.copy(name = "toListOrNull", description = Some("Convert to a list or null in case of failure")),
  ).groupBy(_.name)

  private val key   = "key"
  private val value = "value"

  override type ExtensionMethodInvocationTarget = CollectionConversionExt
  override val invocationTargetClass: Class[CollectionConversionExt] = classOf[CollectionConversionExt]

  override def createConverter(
      classLoader: ClassLoader,
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[CollectionConversionExt] =
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
          if fields.contains(key) && fields.contains(value) =>
        val params = List(fields.get(key), fields.get(value)).flatten
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
      case TypedClass(_, params) => Typed.genericTypeClass[JList[_]](params).validNel
      case Unknown               => Typed.genericTypeClass[JList[_]](List(Unknown)).validNel
      case _                     => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

}
