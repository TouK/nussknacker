package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{
  Typed,
  TypedClass,
  TypedObjectTypingResult,
  TypedObjectWithValue,
  TypingResult,
  Unknown
}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastMethodDefinitions._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.util
import scala.util.Try

sealed trait Cast {
  def canCastTo(className: String): Boolean
  def castTo[T](className: String): T
  def castToOrNull[T >: Null](className: String): T
  def canCastToRecord[T <: java.util.Map[String, _]](record: T): Boolean
  def castToRecord[T <: java.util.Map[String, _]](record: T): T
  def castToRecordOrNull[T >: Null <: java.util.Map[String, _]](record: T): T
}

class CastImpl(target: Any, classLoader: ClassLoader) extends Cast {
  import scala.jdk.CollectionConverters._
  private type RecordType = util.Map[String, _]

  override def canCastTo(className: String): Boolean =
    classLoader.loadClass(className).isAssignableFrom(target.getClass)

  override def castTo[T](className: String): T = {
    val clazz = classLoader.loadClass(className)
    if (clazz.isInstance(target)) {
      clazz.cast(target).asInstanceOf[T]
    } else {
      throw new ClassCastException(s"Cannot cast: ${target.getClass} to: $className")
    }
  }

  override def castToOrNull[T >: Null](className: String): T = Try { castTo[T](className) }.getOrElse(null)

  override def canCastToRecord[T <: RecordType](record: T): Boolean =
    (Typed.fromInstance(target).withoutValue, Typed.fromInstance(record).withoutValue) match {
      case (t1: TypedObjectTypingResult, t2: TypedObjectTypingResult) => t1.canBeSubclassOf(t2)
      case _                                                          => false
    }

  override def castToRecord[T <: RecordType](record: T): T = {
    val currentType = Typed.fromInstance(target).withoutValue
    val targetType  = Typed.fromInstance(record).withoutValue
    (currentType, targetType) match {
      case (t1: TypedObjectTypingResult, t2: TypedObjectTypingResult) if t1.canBeSubclassOf(t2) =>
        filterOutMapElements(target, targetType).asInstanceOf[T]
      case _ => throw new ClassCastException(s"Cannot cast: $target to: $record")
    }
  }

  override def castToRecordOrNull[T >: Null <: util.Map[String, _]](record: T): T =
    Try(castToRecord(record)).getOrElse(null)

  private def filterOutMapElements(target: Any, typingResult: TypingResult): Any =
    (target, typingResult) match {
      case (m: util.Map[_, _], TypedObjectTypingResult(fields, runtime, _)) if runtime.klass.isInstance(m) =>
        m.asScala
          .map { case (k, v) =>
            k -> fields.get(k.asInstanceOf[String]).map(t => filterOutMapElements(v, t))
          }
          .collect { case (k, Some(v)) =>
            k -> v
          }
          .asJava
      case (l: util.List[_], TypedClass(klass, param :: Nil)) if klass.isInstance(l) =>
        l.asScala
          .map(e => filterOutMapElements(e, param))
          .asJava
      case (obj, _) => obj
    }

}

private[extension] object CastImpl extends ExtensionMethodsImplFactory {
  override def create(target: Any, classLoader: ClassLoader): Any =
    new CastImpl(target, classLoader)
}

private[extension] class CastMethodDefinitions(private val classesWithTyping: Map[Class[_], TypingResult]) {

  def extractDefinitions(clazz: Class[_]): Map[String, List[MethodDefinition]] = {
    val childTypes = classesWithTyping.filterKeysNow(targetClazz =>
      clazz != targetClazz &&
        clazz.isAssignableFrom(targetClazz)
    )
    childTypes match {
      case allowedClasses if allowedClasses.isEmpty => Map.empty
      case allowedClasses                           => definitions(allowedClasses)
    }
  }

  private def definitions(allowedClasses: Map[Class[_], TypingResult]): Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (_, x) => canCastToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        "canCastTo",
        Some("Checks if a type can be cast to a given class")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        "castTo",
        Some("Casts a type to a given class or throws exception if type cannot be cast.")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        "castToOrNull",
        Some("Casts a type to a given class or return null if type cannot be cast.")
      ),
      FunctionalMethodDefinition(
        (_, x) => canCastToRecordTyping(x),
        methodTypeInfoWithRecordParam,
        "canCastToRecord",
        Some("Checks if a type can be casted to a given record")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToRecordTyping(x),
        methodTypeInfoWithRecordParam,
        "castToRecord",
        Some("Casts a type to a given record or throws exception if type cannot be casted.")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToRecordTyping(x),
        methodTypeInfoWithRecordParam,
        "castToRecordOrNull",
        Some("Casts a type to a given record or return null if type cannot be casted.")
      ),
    ).groupBy(_.name)

  private def castToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses.find(_._1.getName == clazzName).map(_._2) match {
        case Some(typing) => typing.validNel
        case None         => GenericFunctionTypingError.OtherError(s"Casting to '$clazzName' is not allowed").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canCastToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToTyping(allowedClasses)(arguments).map(_ => Typed.typedClass[Boolean])

  private def castToRecordTyping(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectTypingResult(fields, rot: TypedClass, additionalInfo) :: Nil =>
      Typed
        .record(
          fields.mapValuesNow(_.withoutValue),
          Typed.genericTypeClass(rot.klass, rot.params.map(_.withoutValue)),
          additionalInfo
        )
        .validNel
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canCastToRecordTyping(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToRecordTyping(arguments).map(_ => Typed.typedClass[Boolean])

}

object CastMethodDefinitions {
  private val stringClass  = classOf[String]
  private val mapClass     = classOf[java.util.Map[_, _]]
  private val stringTyping = Typed.genericTypeClass(stringClass, Nil)
  private val mapTyping    = Typed.genericTypeClass(mapClass, List(stringTyping, Unknown))

  private val methodTypeInfoWithStringParam = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", stringTyping)
    ),
    varArg = None,
    result = Unknown
  )

  private val methodTypeInfoWithRecordParam = MethodTypeInfo(
    noVarArgs = List(
      Parameter("record", mapTyping)
    ),
    varArg = None,
    result = Unknown
  )

  def apply(set: ClassDefinitionSet): CastMethodDefinitions =
    new CastMethodDefinitions(
      set.classDefinitionsMap
        .mapValuesNow(_.clazzName)
    )

}
