package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastMethodDefinitions._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.classes.Extensions.{ClassExtensions, ClassesExtensions}

import scala.util.Try

sealed trait Cast {
  def canCastTo(className: String): Boolean
  def castTo[T](className: String): T
  def castToOrNull[T >: Null](className: String): T
}

object Cast {
  private[extension] val canCastToMethodName    = "canCastTo"
  private[extension] val castToMethodName       = "castTo"
  private[extension] val castToOrNullMethodName = "castToOrNull"

  private val castMethodsNames = Set(
    canCastToMethodName,
    castToMethodName,
    castToOrNullMethodName,
  )

  def isCastMethod(methodName: String): Boolean =
    castMethodsNames.contains(methodName)

  def allowedClasses(set: ClassDefinitionSet, clazz: Class[_]): Map[Class[_], TypingResult] = {
    val childTypes = set.classDefinitionsMap
      .filterKeysNow(targetClazz => targetClazz.isChildOf(clazz) && targetClazz.isNotFromNuUtilPackage())
      .mapValuesNow(_.clazzName)
    childTypes
  }

}

class CastImpl(target: Any, classLoader: ClassLoader, classesBySimpleName: Map[String, Class[_]]) extends Cast {

  override def canCastTo(className: String): Boolean =
    getClass(className).isAssignableFrom(target.getClass)

  override def castTo[T](className: String): T = {
    val clazz = getClass(className)
    if (clazz.isInstance(target)) {
      clazz.cast(target).asInstanceOf[T]
    } else {
      throw new ClassCastException(s"Cannot cast: ${target.getClass} to: $className")
    }
  }

  override def castToOrNull[T >: Null](className: String): T =
    Try { castTo[T](className) }.getOrElse(null)

  private def getClass(name: String): Class[_] = classesBySimpleName.get(name) match {
    case Some(clazz) => clazz
    case None        => classLoader.loadClass(name)
  }

}

private[extension] class CastImplFactory(classLoader: ClassLoader, classesBySimpleName: Map[String, Class[_]])
    extends ExtensionMethodsImplFactory {
  override def create(target: Any): Any = new CastImpl(target, classLoader, classesBySimpleName)
}

private[extension] object CastImplFactory {

  def apply(classLoader: ClassLoader, classDefinitionSet: ClassDefinitionSet): CastImplFactory = {
    new CastImplFactory(classLoader, classDefinitionSet.classDefinitionsMap.keySet.classesBySimpleNames())
  }

}

private[extension] class CastMethodDefinitions(set: ClassDefinitionSet) {

  def extractDefinitions(clazz: Class[_]): Map[String, List[MethodDefinition]] = {
    Cast.allowedClasses(set, clazz) match {
      case allowedClasses if allowedClasses.isEmpty => Map.empty
      case allowedClasses                           => definitions(allowedClasses)
    }
  }

  private def definitions(allowedClasses: Map[Class[_], TypingResult]): Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (_, x) => canCastToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.canCastToMethodName,
        Some("Checks if a type can be cast to a given class")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.castToMethodName,
        Some("Casts a type to a given class or throws exception if type cannot be cast.")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.castToOrNullMethodName,
        Some("Casts a type to a given class or return null if type cannot be cast.")
      ),
    ).groupBy(_.name)

  private def castToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses
        .find(e =>
          e._1.getName.equalsIgnoreCase(clazzName) ||
            ReflectUtils.simpleNameWithoutSuffix(e._1).equalsIgnoreCase(clazzName)
        )
        .map(_._2) match {
        case Some(typing) => typing.validNel
        case None =>
          GenericFunctionTypingError.OtherError(s"Casting to '$clazzName' is not allowed").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canCastToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToTyping(allowedClasses)(arguments).map(_ => Typed.typedClass[Boolean])

}

object CastMethodDefinitions {
  private val stringClass = classOf[String]

  private val methodTypeInfoWithStringParam = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", Typed.genericTypeClass(stringClass, Nil))
    ),
    varArg = None,
    result = Unknown
  )

}
