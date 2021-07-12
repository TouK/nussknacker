package pl.touk.nussknacker.engine.api.typed

import io.circe.Encoder
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.util.NotNothing
import pl.touk.nussknacker.engine.api.util.ReflectUtils

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

object typing {

  object TypingResult {
    implicit val encoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder
  }

  sealed trait TypingResult {

    final def canBeSubclassOf(typingResult: TypingResult): Boolean =
      CanBeSubclassDeterminer.canBeSubclassOf(this, typingResult).isValid

    def display: String

    def javaClassName :String

  }

  sealed trait KnownTypingResult extends TypingResult

  sealed trait SingleTypingResult extends KnownTypingResult {

    def objType: TypedClass

  }

  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
      TypedObjectTypingResult(definition.fields.map { case (k, v) => (k, Typed(v))})

    def apply(fields: List[(String, TypingResult)]): TypedObjectTypingResult =
      TypedObjectTypingResult(ListMap(fields: _*))

    def apply(fields: List[(String, TypingResult)], objType: TypedClass): TypedObjectTypingResult =
      TypedObjectTypingResult(ListMap(fields: _*), objType)

    def apply(fields: ListMap[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, TypedClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown)))
  }

  // Warning, fields are kept in list-like map: 1) order is important 2) lookup has O(n) complexity
  case class TypedObjectTypingResult(fields: ListMap[String, TypingResult],
                                     objType: TypedClass,
                                     additionalInfo: Map[String, AdditionalDataValue] = Map.empty) extends SingleTypingResult {

    override def display: String = fields.map { case (name, typ) => s"$name: ${typ.display}"}.mkString("{", ", ", "}")

    override def javaClassName: String = ???
  }

  case class TypedDict(dictId: String, valueType: SingleTypingResult) extends SingleTypingResult {

    type ValueType = SingleTypingResult

    override def objType: TypedClass = valueType.objType

    override def display: String = s"Dict(id=$dictId)"

    override def javaClassName: String = ???
  }

  case class TypedTaggedValue(underlying: SingleTypingResult, tag: String) extends SingleTypingResult {

    override def objType: TypedClass = underlying.objType

    override def display: String = s"${underlying.display} @ $tag"

    override def javaClassName: String = ???
  }

  // Unknown is representation of TypedUnion of all possible types
  case object Unknown extends TypingResult {

    override val display = "Unknown"

    override def javaClassName: String = ???
  }

  // constructor is package protected because you should use Typed.apply to be sure that possibleTypes.size > 1
  case class TypedUnion private[typing](possibleTypes: Set[SingleTypingResult]) extends KnownTypingResult {

    assert(possibleTypes.size != 1, "TypedUnion should has zero or more than one possibleType - in other case should be used TypedObjectTypingResult or TypedClass")

    override val display : String = possibleTypes.toList match {
      case Nil => "EmptyUnion"
      case many => many.map(_.display).mkString(" | ")
    }

    override def javaClassName: String = ???
  }

  //TODO: make sure parameter list has right size - can be filled with Unknown if needed
  case class TypedClass private[typing] (klass: Class[_], params: List[TypingResult]) extends SingleTypingResult {

    override def javaClassName :String = ReflectUtils.fixedClassSimpleNameWithoutParentModule(ClassUtils.primitiveToWrapper(klass))

    //TODO: should we use simple name here?
    override def display: String = {
      val className = ReflectUtils.fixedClassSimpleNameWithoutParentModule(ClassUtils.primitiveToWrapper(klass))
      if (params.nonEmpty)
        s"$className[${params.map(_.display).mkString(",")}]"
      else
        s"$className"
    }

    override def objType: TypedClass = this

  }

  object Typed {

    //TODO: how to assert in compile time that T != Any, AnyRef, Object?
    def typedClass[T: ClassTag]: TypedClass = TypedClass(toRuntime[T], Nil)

    //TODO: make it more safe??
    def typedClass(klass: Class[_]): TypedClass = if (klass == classOf[Any]) {
      throw new IllegalArgumentException("Cannot have typed class of Any, use Unknown")
    } else {
      TypedClass(klass, Nil)
    }

    def genericTypeClass(klass: Class[_], params: List[TypingResult]): TypingResult = TypedClass(klass, params)

    def genericTypeClass[T:ClassTag](params: List[TypingResult]): TypingResult = TypedClass(toRuntime[T], params)

    def empty: TypedUnion = TypedUnion(Set.empty)

    def apply[T: ClassTag]: TypingResult = apply(toRuntime[T])

    /*using TypeTag can give better description (with extracted generic parameters), however:
      - in runtime/production we usually don't have TypeTag, as we rely on reflection anyway
      - one should be *very* careful with TypeTag as it degrades performance significantly when on critical path (e.g. SpelExpression.evaluate)
     */
    def fromDetailedType[T: TypeTag: NotNothing]: TypingResult = {
      val tag = typeTag[T]
      // is it correct mirror?
      implicit val mirror: Mirror = tag.mirror
      fromType(tag.tpe)
    }

    private def fromType(typ: Type)(implicit mirror: Mirror): TypingResult = {
      val runtimeClass = mirror.runtimeClass(typ.erasure)
      if (runtimeClass == classOf[Any])
        Unknown
      else
        TypedClass(runtimeClass, typ.typeArgs.map(fromType))
    }

    private def toRuntime[T:ClassTag]: Class[_] = implicitly[ClassTag[T]].runtimeClass

    def apply(klass: Class[_]): TypingResult = {
      if (klass == classOf[Any]) Unknown else TypedClass(klass, Nil)
    }

    def taggedDictValue(typ: SingleTypingResult, dictId: String): TypedTaggedValue = tagged(typ, s"dictValue:$dictId")

    def tagged(typ: SingleTypingResult, tag: String): TypedTaggedValue = TypedTaggedValue(typ, tag)

    def fromInstance(obj: Any): TypingResult = {
      obj match {
        case null =>
          Typed.empty
        case map: Map[String@unchecked, _]  =>
          val fieldTypes = typeMapFields(map)
          TypedObjectTypingResult(fieldTypes, TypedClass(classOf[Map[_, _]], List(Typed[String], Unknown)))
        case javaMap: java.util.Map[String@unchecked, _] =>
          val fieldTypes = typeMapFields(javaMap.asScala.toMap)
          TypedObjectTypingResult(fieldTypes)
        case list: List[_] =>
          TypedClass(obj.getClass, List(unionOfElementTypes(list)))
        case javaList: java.util.List[_] =>
          TypedClass(obj.getClass, List(unionOfElementTypes(javaList.asScala.toList)))
        case dict: DictInstance =>
          TypedDict(dict.dictId, dict.valueType)
        case other =>
          Typed(other.getClass)
      }
    }

    private def typeMapFields(map: Map[String, _]) = map.map {
        case (k, v) => k -> fromInstance(v)
      }.toList

    private def unionOfElementTypes(list: List[_]): TypingResult = {
      apply(list.map(fromInstance).toSet)
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

  object AdditionalDataValue {

    implicit def string(value: String): AdditionalDataValue = StringValue(value)

    implicit def long(value: Long): AdditionalDataValue = LongValue(value)

    implicit def boolean(value: Boolean): AdditionalDataValue = BooleanValue(value)

  }

  sealed trait AdditionalDataValue

  case class StringValue(value: String) extends AdditionalDataValue

  case class LongValue(value: Long) extends AdditionalDataValue

  case class BooleanValue(value: Boolean) extends AdditionalDataValue


}
