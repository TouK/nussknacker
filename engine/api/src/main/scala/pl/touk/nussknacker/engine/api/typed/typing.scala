package pl.touk.nussknacker.engine.api.typed

import java.util

import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canHasAnyPropertyOrField: Boolean

    final def canBeSubclassOf(typingResult: TypingResult): Boolean =
      typing.canBeSubclassOf(this, typingResult)

    def display: String

  }

  sealed trait KnownTypingResult extends TypingResult

  sealed trait ScalarTypingResult extends KnownTypingResult {

    def objType: TypedClass

  }

  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
    //we don't use mapValues here to avoid lazy evaluation that crashes during serialization...
      TypedObjectTypingResult(definition.fields.map { case (k, v) => (k, Typed(v))})

    def apply(fields: Map[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, TypedClass[java.util.Map[_, _]])

  }

  case class TypedObjectTypingResult(fields: Map[String, TypingResult], objType: TypedClass) extends ScalarTypingResult {

    override def canHasAnyPropertyOrField: Boolean = false

    override def display: String = s"object with fields:${fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString(", ")}"

  }

  // Unknown is representation of TypedUnion of all possible types
  case object Unknown extends TypingResult {

    override def canHasAnyPropertyOrField: Boolean = true

    override val display = "unknown"

  }

  // constructor is package protected because you should use Typed.apply to be sure that possibleTypes.size > 1
  case class TypedUnion private[typing](private[typing] val possibleTypes: Set[ScalarTypingResult]) extends KnownTypingResult {

    assert(possibleTypes.size != 1, "TypedUnion should has zero or more than one possibleType - in other case should be used TypedObjectTypingResult or TypedClass")

    override def canHasAnyPropertyOrField: Boolean = {
      possibleTypes.exists(_.canHasAnyPropertyOrField)
    }

    override val display : String = possibleTypes.toList match {
      case Nil => "empty"
      case many => many.map(_.display).mkString("one of (", ", ", ")")
    }

  }

  //TODO: make sure parameter list has right size - can be filled with Unknown if needed
  case class TypedClass(klass: Class[_], params: List[TypingResult]) extends ScalarTypingResult {

    override def canHasAnyPropertyOrField: Boolean =
      typing.canBeSubclassOf(this, Typed[util.Map[_, _]]) || hasGetFieldByNameMethod

    private def hasGetFieldByNameMethod =
      klass.getMethods.exists(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))

    //TODO: should we use simple name here?
    override def display: String = s"type '${klass.getName}'"

    override def objType: TypedClass = this

  }

  object TypedClass {

    def apply[T:ClassTag] : TypedClass =
      TypedClass(ClazzRef[T])

    def apply(klass: ClazzRef) : TypedClass =
      TypedClass(klass.clazz, klass.params.map(Typed.apply))

  }

  private def canBeSubclassOf(first: TypingResult, sec: TypingResult): Boolean =
    (first, sec) match {
      case (_, Unknown) => true
      case (Unknown, _) => true
      case (f: ScalarTypingResult, s: TypedUnion) => canBeSubclassOf(Set(f), s.possibleTypes)
      case (f: TypedUnion, s: ScalarTypingResult) => canBeSubclassOf(f.possibleTypes, Set(s))
      case (f: ScalarTypingResult, s: ScalarTypingResult) => scalarCanBeSubclassOf(f, s)
      case (f: TypedUnion, s: TypedUnion) => canBeSubclassOf(f.possibleTypes, s.possibleTypes)
    }

  private def canBeSubclassOf(firstSet: Set[ScalarTypingResult], secSet: Set[ScalarTypingResult]): Boolean =
    firstSet.exists(f => secSet.exists(scalarCanBeSubclassOf(f, _)))

  private def scalarCanBeSubclassOf(first: ScalarTypingResult, sec: ScalarTypingResult): Boolean = {
    def additionalRestrictions = sec match {
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
    klassCanBeSubclassOf(first.objType, sec.objType) && additionalRestrictions
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

    def apply[T:ClassTag] : TypingResult = apply(ClazzRef[T])

    def apply(klass: Class[_]): TypingResult = apply(ClazzRef(klass))

    def apply(klass: ClazzRef) : TypingResult = {
      if (klass == ClazzRef.unknown) {
        Unknown
      } else {
        TypedClass(klass)
      }
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
      case other: ScalarTypingResult => List(other)
    }

  }

}
