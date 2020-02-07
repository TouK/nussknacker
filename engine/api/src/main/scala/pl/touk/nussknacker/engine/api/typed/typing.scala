package pl.touk.nussknacker.engine.api.typed

import java.util

import cats.data.NonEmptyList
import io.circe.Encoder
import pl.touk.nussknacker.engine.api.dict.DictInstance

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
      TypedObjectTypingResult(fields, Typed.typedClass[java.util.Map[_, _]])

  }

  case class TypedObjectTypingResult(fields: Map[String, TypingResult], objType: TypedClass) extends SingleTypingResult {

    override def canHasAnyPropertyOrField: Boolean = false

    override def display: String = fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString("object with fields: ", ", ", "")

  }

  case class TypedDict(dictId: String, valueType: SingleTypingResult) extends SingleTypingResult {

    type ValueType = SingleTypingResult

    override def canHasAnyPropertyOrField: Boolean = false

    override def objType: TypedClass = valueType.objType

    override def display: String = s"dict with id: '$dictId'"

  }

  case class TypedTaggedValue(underlying: SingleTypingResult, tag: String) extends SingleTypingResult {

    override def canHasAnyPropertyOrField: Boolean = underlying.canHasAnyPropertyOrField

    override def objType: TypedClass = underlying.objType

    override def display: String = s"tagged: ${underlying.display} by tag: '$tag'"

  }

  // Unknown is representation of TypedUnion of all possible types
  case object Unknown extends TypingResult {

    override def canHasAnyPropertyOrField: Boolean = true

    override val display = "unknown"

  }

  // constructor is package protected because you should use Typed.apply to be sure that possibleTypes.size > 1
  case class TypedUnion private[typing](possibleTypes: Set[SingleTypingResult]) extends KnownTypingResult {

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
  case class TypedClass private[typing] (klass: Class[_], params: List[TypingResult]) extends SingleTypingResult {

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

  object Typed {

    //TODO: how to assert in compile time that T != Any, AnyRef, Object?
    def typedClass[T: ClassTag] = TypedClass(toRuntime[T], Nil)

    //TODO: make it more safe??
    def typedClass(klass: Class[_]): TypedClass = if (klass == classOf[Any]) {
      throw new IllegalArgumentException("Cannot have typed class of Any, use Unknown")
    } else {
      TypedClass(klass, Nil)
    }

    def genericTypeClass(klass: Class[_], params: List[TypingResult]): TypingResult = TypedClass(klass, params)

    def genericTypeClass[T:ClassTag](params: List[TypingResult]): TypingResult = TypedClass(toRuntime[T], params)

    def empty = TypedUnion(Set.empty)

    def apply[T: ClassTag]: TypingResult = apply(toRuntime[T])

    /*using TypeTag can give better description (with extracted generic parameters), however:
      - in runtime/production we usually don't have TypeTag, as we rely on reflection anyway
      - one should be *very* careful with TypeTag as it degrades performance significantly when on critical path (e.g. SpelExpression.evaluate)
     */
    def fromDetailedType[T: TypeTag]: TypingResult = {
      val tag = typeTag[T]
      // is it correct mirror?
      implicit val mirror: Mirror = tag.mirror
      fromType(tag.tpe)
    }

    private def fromType(typ: Type)(implicit mirror: Mirror): TypedClass = {
      val runtimeClass = mirror.runtimeClass(typ.erasure)
      TypedClass(runtimeClass, typ.typeArgs.map(fromType))
    }

    private def toRuntime[T:ClassTag]: Class[_] = implicitly[ClassTag[T]].runtimeClass

    def apply(klass: Class[_]): TypingResult = {
      if (klass == classOf[Any]) Unknown else TypedClass(klass, Nil)
    }

    def taggedDictValue(typ: SingleTypingResult, dictId: String): TypedTaggedValue = tagged(typ, s"dictValue:$dictId")

    def tagged(typ: SingleTypingResult, tag: String) = TypedTaggedValue(typ, tag)

    def fromInstance(obj: Any): TypingResult = {
      obj match {
        case null =>
          Typed.empty
        case TypedMap(fields) =>
          val fieldTypes = fields.map {
            case (k, v) => k -> fromInstance(v)
          }
          TypedObjectTypingResult(fieldTypes, TypedClass(classOf[TypedMap], Nil))
        case dict: DictInstance =>
          TypedDict(dict.dictId, dict.valueType)
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

    private def flatten(possibleTypes: List[KnownTypingResult]): List[SingleTypingResult] = possibleTypes.flatMap {
      case TypedUnion(possibleTypes) => possibleTypes
      case other: SingleTypingResult => List(other)
    }

  }

}
