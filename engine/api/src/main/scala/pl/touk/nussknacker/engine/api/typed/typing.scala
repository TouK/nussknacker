package pl.touk.nussknacker.engine.api.typed

import java.util

import io.circe.Encoder
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.ArgonautCirce

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object typing {

  object TypingResult {
    implicit val encoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder
  }

  sealed trait TypingResult {

    def canHasAnyPropertyOrField: Boolean

    final def canBeSubclassOf(typingResult: TypingResult): Boolean =
      typing.canBeSubclassOf(this, typingResult)

    def display: String

  }

  sealed trait KnownTypingResult extends TypingResult

  sealed trait SingleTypingResult extends KnownTypingResult {

    def objType: TypedClass

  }

  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
    //we don't use mapValues here to avoid lazy evaluation that crashes during serialization...
      TypedObjectTypingResult(definition.fields.map { case (k, v) => (k, Typed(v))})

    def apply(fields: Map[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, TypedClass[java.util.Map[_, _]])

  }

  case class TypedObjectTypingResult(fields: Map[String, TypingResult], objType: TypedClass) extends SingleTypingResult {

    override def canHasAnyPropertyOrField: Boolean = false

    override def display: String = fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString("object with fields: ", ", ", "")

  }

  // Unknown is representation of TypedUnion of all possible types
  case object Unknown extends TypingResult {

    override def canHasAnyPropertyOrField: Boolean = true

    override val display = "unknown"

  }

  // constructor is package protected because you should use Typed.apply to be sure that possibleTypes.size > 1
  case class TypedUnion private[typing](private[typing] val possibleTypes: Set[SingleTypingResult]) extends KnownTypingResult {

    assert(possibleTypes.size != 1, "TypedUnion should has zero or more than one possibleType - in other case should be used TypedObjectTypingResult or TypedClass")

    override def canHasAnyPropertyOrField: Boolean = {
      possibleTypes.exists(_.canHasAnyPropertyOrField)
    }

    override val display : String = possibleTypes.toList match {
      case Nil => "empty"
      case many => many.map(_.display).mkString(" | ")
    }

  }

  //TODO: make sure parameter list has right size - can be filled with Unknown if needed
  case class TypedClass(klass: Class[_], params: List[TypingResult]) extends SingleTypingResult {

    override def canHasAnyPropertyOrField: Boolean =
      typing.canBeSubclassOf(this, Typed[util.Map[_, _]]) || hasGetFieldByNameMethod

    private def hasGetFieldByNameMethod =
      klass.getMethods.exists(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))

    //TODO: should we use simple name here?
    override def display: String = {
      if (params.nonEmpty)
        s"${klass.getName}[${params.map(_.display).mkString(",")}]"
      else
        s"${klass.getName}"
    }

    override def objType: TypedClass = this

  }

  object TypedClass {

    def apply[T: ClassTag] : TypedClass =
      TypedClass(ClazzRef[T])

    def apply(klass: ClazzRef) : TypedClass =
      TypedClass(klass.clazz, klass.params.map(Typed.apply))

  }

  private def canBeSubclassOf(first: TypingResult, sec: TypingResult): Boolean =
    (first, sec) match {
      case (_, Unknown) => true
      case (Unknown, _) => true
      case (f: SingleTypingResult, s: TypedUnion) => canBeSubclassOf(Set(f), s.possibleTypes)
      case (f: TypedUnion, s: SingleTypingResult) => canBeSubclassOf(f.possibleTypes, Set(s))
      case (f: SingleTypingResult, s: SingleTypingResult) => singleCanBeSubclassOf(f, s)
      case (f: TypedUnion, s: TypedUnion) => canBeSubclassOf(f.possibleTypes, s.possibleTypes)
    }

  private def canBeSubclassOf(firstSet: Set[SingleTypingResult], secSet: Set[SingleTypingResult]): Boolean =
    firstSet.exists(f => secSet.exists(singleCanBeSubclassOf(f, _)))

  private def singleCanBeSubclassOf(first: SingleTypingResult, sec: SingleTypingResult): Boolean = {
    def typedObjectRestrictions = sec match {
      case s: TypedObjectTypingResult =>
        val firstFields = first match {
          case f: TypedObjectTypingResult => f.fields
          case _ => Map.empty[String, TypingResult]
        }
        s.fields.forall {
          case (name, typ) => firstFields.get(name).exists(canBeSubclassOf(_, typ))
        }
      case _ =>
        true
    }
    klassCanBeSubclassOf(first.objType, sec.objType) && typedObjectRestrictions
  }

  private def klassCanBeSubclassOf(first: TypedClass, sec: TypedClass): Boolean = {
    def hasSameTypeParams =
      //we are lax here - the generic type may be co- or contra-variant - and we don't want to
      //throw validation errors in this case. It's better to accept to much than too little
      sec.params.zip(first.params).forall(t => canBeSubclassOf(t._1, t._2) || canBeSubclassOf(t._2, t._1))

    first == sec || ClassUtils.isAssignable(first.klass, sec.klass) && hasSameTypeParams
  }

  object Typed {

    def empty = TypedUnion(Set.empty)

    def apply[T: ClassTag]: TypingResult = apply(ClazzRef[T])

    def detailed[T: TypeTag]: TypingResult = apply(ClazzRef.detailed[T])

    def apply(klass: Class[_]): TypingResult = apply(ClazzRef(klass))

    def apply(klass: ClazzRef): TypingResult = {
      // TODO: make creating unknown type more explicit and fix places where we have Typed type instead of TypedClass | Unknown
      if (klass == ClazzRef.unknown) {
        Unknown
      } else {
        TypedClass(klass)
      }
    }

    def apply(possibleTypes: TypingResult*): TypingResult = {
      apply(possibleTypes.toSet)
    }

    // creates Typed representation of sum of possible types
    def apply[T <: TypingResult](possibleTypes: Set[T]): TypingResult = {
      if (possibleTypes.exists(_ == Unknown)) {
        Unknown
      } else {
        // we are sure know that there is no Unknown type inside
        flatten(possibleTypes.toList.asInstanceOf[List[KnownTypingResult]]).distinct match {
          case Nil =>
            Typed.empty
          case single :: Nil =>
            single
          case moreThanOne =>
            TypedUnion(moreThanOne.toSet)
        }
      }
    }

    private def flatten(possibleTypes: List[KnownTypingResult]) = possibleTypes.flatMap {
      case TypedUnion(possibleTypes) => possibleTypes
      case other: SingleTypingResult => List(other)
    }

  }

}
