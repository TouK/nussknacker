package pl.touk.nussknacker.engine.api.typed

import java.util

import io.circe.Encoder

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object typing {

  object TypingResult {
    implicit val encoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder
  }

  sealed trait TypingResult {

    def canHasAnyPropertyOrField: Boolean

    final def canBeSubclassOf(typingResult: TypingResult): Boolean =
      CanBeSubclassDeterminer.canBeSubclassOf(this, typingResult)

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
  case class TypedUnion private[typing](private[typed] val possibleTypes: Set[SingleTypingResult]) extends KnownTypingResult {

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
      CanBeSubclassDeterminer.canBeSubclassOf(this, Typed[util.Map[_, _]]) || hasGetFieldByNameMethod

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

  object Typed {

    def empty = TypedUnion(Set.empty)

    def apply[T: ClassTag]: TypingResult = apply(ClazzRef[T])

    def fromDetailedType[T: TypeTag]: TypingResult = apply(ClazzRef.fromDetailedType[T])

    def apply(klass: Class[_]): TypingResult = apply(ClazzRef(klass))

    def apply(klass: ClazzRef): TypingResult = {
      // TODO: make creating unknown type more explicit and fix places where we have Typed type instead of TypedClass | Unknown
      if (klass == ClazzRef.unknown) {
        Unknown
      } else {
        TypedClass(klass)
      }
    }

    def fromInstance(obj: Any): TypingResult = {
      obj match {
        case null =>
          Typed.empty
        case TypedMap(fields) =>
          val fieldTypes = fields.map {
            case (k, v) => k -> fromInstance(v)
          }
          TypedObjectTypingResult(fieldTypes, TypedClass[TypedMap])
        case other =>
          Typed(other.getClass)
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
