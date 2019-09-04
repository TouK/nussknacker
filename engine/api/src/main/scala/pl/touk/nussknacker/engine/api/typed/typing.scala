package pl.touk.nussknacker.engine.api.typed

import java.util

import cats.kernel.CommutativeMonoid
import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canHasAnyPropertyOrField: Boolean

    def canBeSubclassOf(typingResult: TypingResult) : Boolean

    def display: String

    def objType: TypedClass

  }

  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
    //we don't use mapValues here to avoid lazy evaluation that crashes during serialization...
      TypedObjectTypingResult(definition.fields.map { case (k, v) => (k, Typed(v))})

    def apply(fields: Map[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, TypedClass[java.util.Map[_, _]])

  }

  case class TypedObjectTypingResult(fields: Map[String, TypingResult], objType: TypedClass) extends TypingResult {

    override def canBeSubclassOf(typingResult: TypingResult): Boolean = typingResult match {
      case TypedObjectTypingResult(otherFields, _) =>
        objType.canBeSubclassOf(typingResult) && otherFields.forall {
          case (name, typ) => fields.get(name).exists(_.canBeSubclassOf(typ))
        }
      case _ =>
        objType.canBeSubclassOf(typingResult)
    }

    override def canHasAnyPropertyOrField: Boolean = false

    override def display: String = s"object with fields:${fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString(", ")}"

  }

  case object Unknown extends TypingResult {
    override def canBeSubclassOf(typingResult: TypingResult): Boolean = true

    override def canHasAnyPropertyOrField: Boolean = true

    override val display = "unknown"

    override def objType: TypedClass = TypedClass[Any]
  }

  // constructor is package protected because you should use Typed.apply to be sure that possibleTypes.size > 1
  case class TypedUnion private[typing](private val possibleTypes: Set[TypingResult]) extends TypingResult {

    assert(possibleTypes.size > 1, "TypedUnion should has more than one possibleType - in other case should be used TypedObjectTypingResult or TypedClass")

    override def canBeSubclassOf(typingResult: TypingResult): Boolean = {
      possibleTypes.exists(_.canBeSubclassOf(typingResult))
    }

    override def canHasAnyPropertyOrField: Boolean = {
      possibleTypes.exists(_.canHasAnyPropertyOrField)
    }

    override val display : String = possibleTypes.toList match {
      case Nil => "empty"
      case many => many.map(_.display).mkString("one of (", ", ", ")")
    }

    // TODO: we should probably look for lowest common ancestor
    override def objType: TypedClass = TypedClass[Any]

  }

  //TODO: make sure parameter list has right size - can be filled with Unknown if needed
  case class TypedClass(klass: Class[_], params: List[TypingResult]) extends TypingResult {

    def canBeSubclassOf(other: TypingResult): Boolean = {
      def hasSameTypeParams =
        //we are lax here - the generic type may be co- or contra-variant - and we don't want to
        //throw validation errors in this case. It's better to accept to much than too little
        other.objType.params.zip(params).forall(t => t._1.canBeSubclassOf(t._2) || t._2.canBeSubclassOf(t._1))

      this == other || ClassUtils.isAssignable(klass, other.objType.klass) && (hasSameTypeParams || other.canHasAnyPropertyOrField)
    }

    override def canHasAnyPropertyOrField: Boolean =
      canBeSubclassOf(Typed[util.Map[_, _]]) || hasGetFieldByNameMethod

    private def hasGetFieldByNameMethod =
      klass.getMethods.exists(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))

    //TODO: should we use simple name here?
    override def display: String = s"type '${klass.getName}'"

    override def objType: TypedClass = this

  }

  object TypedClass {

    private[typed] def apply[T:ClassTag] : TypedClass =
      TypedClass(ClazzRef[T])

    private[typed] def apply(klass: ClazzRef) : TypedClass =
      TypedClass(klass.clazz, klass.params.map(Typed.apply))

  }

  object Typed {

    def apply[T:ClassTag] : TypingResult = apply(ClazzRef[T])

    def apply(klass: Class[_]): TypingResult = apply(ClazzRef(klass))

    def apply(klass: ClazzRef) : TypingResult = {
      if (klass == ClazzRef.unknown) {
        Unknown
      } else {
        TypedClass(klass)
      }
    }

    def apply[T <: TypingResult](possibleTypes: Set[T]): TypingResult = {
      reduceUnknowns(flatten(possibleTypes.toSet[TypingResult])).toList match {
        case Nil =>
          throw new IllegalArgumentException("Can't create Typed with empty possible types")
        case single :: Nil =>
          single
        case moreThanOne =>
          TypedUnion(moreThanOne.toSet[TypingResult])
      }
    }

    private def flatten(possibleTypes: Set[TypingResult]) = possibleTypes.flatMap {
      case TypedUnion(possibleTypes) => possibleTypes
      case other => Set(other)
    }

    private def reduceUnknowns(possibleTypes: Set[TypingResult]) =
      if (possibleTypes.contains(Unknown) && possibleTypes.size > 1) {
        possibleTypes - Unknown
      } else {
        possibleTypes
      }

  }

  implicit val commutativeMonoid: CommutativeMonoid[TypingResult] = new CommutativeMonoid[TypingResult] {

    //ha... is it really this way? :)
    override def empty: TypingResult = Typed[Any]

    override def combine(x: TypingResult, y: TypingResult): TypingResult =
      Typed(Set(x, y))

  }

}
