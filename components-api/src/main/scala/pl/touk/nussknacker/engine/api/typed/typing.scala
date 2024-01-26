package pl.touk.nussknacker.engine.api.typed

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toTraverseOps
import io.circe.Encoder
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.{
  CommonSupertypeFinder,
  NumberTypesPromotionStrategy,
  SupertypeClassResolutionStrategy
}
import pl.touk.nussknacker.engine.api.typed.typing.Typed.fromInstance
import pl.touk.nussknacker.engine.api.util.{NotNothing, ReflectUtils}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object typing {

  object TypingResult {
    implicit val encoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder
  }

  // TODO: Rename to Typed
  sealed trait TypingResult {

    final def canBeSubclassOf(typingResult: TypingResult): Boolean =
      CanBeSubclassDeterminer.canBeSubclassOf(this, typingResult).isValid

    def valueOpt: Option[Any]

    def withoutValue: TypingResult

    def display: String

  }

  sealed trait KnownTypingResult extends TypingResult

  sealed trait SingleTypingResult extends KnownTypingResult {
    override def withoutValue: SingleTypingResult

    def objType: TypedClass
  }

  // TODO: Rename to TypedRecord
  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
      TypedObjectTypingResult(definition.fields)

    def apply(fields: Map[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, mapBasedRecordUnderlyingType[java.util.Map[_, _]](fields))

    // For backward compatibility, to be removed once downstream projects switch to apply(fields: Map[String, TypingResult])
    def apply(fields: List[(String, TypingResult)]): TypedObjectTypingResult = TypedObjectTypingResult(fields.toMap)
  }

  case class TypedObjectTypingResult(
      fields: Map[String, TypingResult],
      objType: TypedClass,
      additionalInfo: Map[String, AdditionalDataValue] = Map.empty
  ) extends SingleTypingResult {
    override def valueOpt: Option[Map[String, Any]] =
      fields.map { case (k, v) => v.valueOpt.map((k, _)) }.toList.sequence.map(Map(_: _*))

    override def withoutValue: TypedObjectTypingResult =
      TypedObjectTypingResult(fields.mapValuesNow(_.withoutValue), objType, additionalInfo)

    override def display: String =
      fields.map { case (name, typ) => s"$name: ${typ.display}" }.toList.sorted.mkString("Record{", ", ", "}")
  }

  case class TypedDict(dictId: String, valueType: SingleTypingResult) extends SingleTypingResult {

    type ValueType = SingleTypingResult

    override def objType: TypedClass = valueType.objType

    override def valueOpt: Option[Any] = valueType.valueOpt

    override def withoutValue: TypedDict = TypedDict(dictId, valueType.withoutValue)

    override def display: String = s"Dict(id=$dictId)"

  }

  sealed trait TypedObjectWithData extends SingleTypingResult {
    def underlying: SingleTypingResult
    def data: Any

    override def objType: TypedClass = underlying.objType
  }

  case class TypedTaggedValue(underlying: SingleTypingResult, tag: String) extends TypedObjectWithData {
    override def data: String = tag

    override def valueOpt: Option[Any] = underlying.valueOpt

    override def withoutValue: TypedTaggedValue = TypedTaggedValue(underlying.withoutValue, tag)

    override def display: String = s"${underlying.display} @ $tag"
  }

  case class TypedObjectWithValue private[typing] (underlying: TypedClass, value: Any) extends TypedObjectWithData {
    val maxDataDisplaySize: Int         = 15
    val maxDataDisplaySizeWithDots: Int = maxDataDisplaySize - "...".length

    override def data: Any = value

    override def valueOpt: Option[Any] = Some(value)

    override def withoutValue: SingleTypingResult = underlying.withoutValue

    override def display: String = {
      val dataString = data.toString
      val shortenedDataString =
        if (dataString.length <= maxDataDisplaySize) dataString
        else dataString.take(maxDataDisplaySizeWithDots) ++ "..."
      s"${underlying.display}($shortenedDataString)"
    }

  }

  sealed trait WildcardType extends TypingResult

  // The same as in scala's Null type case, only null can be subclass of Null, but on the other hand Null can be subclass of anything
  case object TypedNull extends WildcardType {
    override def withoutValue: TypedNull.type = TypedNull

    // this value is intentionally `Some(null)` (and not `None`), as TypedNull represents null value
    override val valueOpt: Some[Null] = Some(null)

    override val display = "Null"
  }

  // Unknown is representation of TypedUnion of all possible types
  case object Unknown extends WildcardType {
    override def withoutValue: Unknown.type = Unknown

    override val valueOpt: None.type = None

    override val display = "Unknown"
  }

  // constructor is package protected because you should use Typed.apply to be sure that possibleTypes.size >= 2
  class TypedUnion private[typing] (
      private val firstType: SingleTypingResult,
      private val secondType: SingleTypingResult,
      private val restOfTypes: List[SingleTypingResult]
  ) extends KnownTypingResult
      with Serializable {

    override def valueOpt: None.type = None

    override def withoutValue: TypingResult = Typed.fromIterableOrNullType(possibleTypes.map(_.withoutValue).toList)

    override val display: String = possibleTypes.map(_.display).toList.sorted.mkString(" | ")

    def possibleTypes: NonEmptyList[SingleTypingResult] = NonEmptyList(firstType, secondType :: restOfTypes)

    // We implement hashcode / equals because order is not important
    override def hashCode(): Int =
      possibleTypes.toList.toSet.hashCode()

    override def equals(another: Any): Boolean = another match {
      case anotherUnion: TypedUnion => anotherUnion.possibleTypes.toList.toSet == possibleTypes.toList.toSet
      case _                        => false
    }

    override def toString = s"TypedUnion(${possibleTypes.toList.sortBy(_.display).mkString(", ")})"

  }

  object TypedClass {

    // it's vital to have private apply/constructor so that we assure that klass is not primitive nor Any/AnyRef/Object
    private[typing] def apply(klass: Class[_], params: List[TypingResult]) = new TypedClass(klass, params)

  }

  case class TypedClass private[typing] (klass: Class[_], params: List[TypingResult]) extends SingleTypingResult {
    override val valueOpt: None.type = None

    override def withoutValue: TypedClass = this

    override def display: String = {
      val className =
        if (klass.isArray) "Array"
        else ReflectUtils.simpleNameWithoutSuffix(klass)
      if (params.nonEmpty) s"$className[${params.map(_.display).mkString(",")}]"
      else s"$className"
    }

    override def objType: TypedClass = this

    def primitiveClass: Class[_] = Option(ClassUtils.wrapperToPrimitive(klass)).getOrElse(klass)

  }

  object Typed {

    // Below are secure, generic parameters aware variants of typing factory methods

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
        genericTypeClass(runtimeClass, typ.dealias.typeArgs.map(fromType))
    }

    def genericTypeClass[T: ClassTag](params: List[TypingResult]): TypedClass = genericTypeClass(toRuntime[T], params)

    def genericTypeClass(klass: Class[_], params: List[TypingResult]): TypedClass = typedClass(klass, Some(params))

    // Below are not secure variants of typing factory methods - they are need because of Java's type erasure

    def apply[T: ClassTag]: TypingResult = apply(toRuntime[T])

    def apply(klass: Class[_]): TypingResult = {
      if (klass == classOf[Any]) Unknown else typedClass(klass, None)
    }

    // TODO: how to assert in compile time that T != Any, AnyRef, Object?
    // TODO: Those two methods below are very danger - dev can forgot to pass generic parameters which can cause man complications.
    //      Maybe we should do sth to enforce devs to use genericTypeClass variant with explicit list of params and leave it just
    //      for very specific cases - e.g. by renaming it to typedClassUnsafeGenericParams?
    def typedClass[T: ClassTag]: TypedClass = typedClass(toRuntime[T])

    def typedClass(klass: Class[_]): TypedClass = typedClass(klass, None)

    def typedClassOpt[T: ClassTag]: Option[TypedClass] = typedClassOpt(toRuntime[T])

    def typedClassOpt(klass: Class[_]): Option[TypedClass] = Option(Typed(klass)).collect { case cl: TypedClass =>
      cl
    }

    private def toRuntime[T: ClassTag]: Class[_] = implicitly[ClassTag[T]].runtimeClass

    // parameters - None if you are not in generic aware context, Some - otherwise
    private def typedClass(klass: Class[_], parametersOpt: Option[List[TypingResult]]): TypedClass =
      if (klass == classOf[Any]) {
        throw new IllegalArgumentException("Cannot have typed class of Any, use Unknown")
      } else if (klass.isPrimitive) {
        parametersOpt.collect {
          case parameters if parameters.nonEmpty =>
            throw new IllegalArgumentException(
              s"Primitive type: $klass with non empty generic parameters list: $parameters"
            )
        }
        TypedClass(ClassUtils.primitiveToWrapper(klass), List.empty)
      } else if (klass.isArray) {
        determineArrayType(klass, parametersOpt)
      } else {
        determineStandardClassType(klass, parametersOpt)
      }

    // to not have separate class for each array, we pass Array of Objects
    private val KlassForArrays = classOf[Array[Object]]

    private def determineArrayType(klass: Class[_], parameters: Option[List[TypingResult]]): TypedClass = {
      val determinedComponentType = Typed(klass.getComponentType)
      parameters match {
        // it may happen that parameter will be decoded via other means, we have to to sanity check if they match
        case None | Some(`determinedComponentType` :: Nil) =>
          TypedClass(KlassForArrays, List(determinedComponentType))
        // When type is deserialized, in component type will be always Unknown, because w use Array[Object] so we need to use parameters instead
        case Some(notComponentType :: Nil) if determinedComponentType == Unknown =>
          TypedClass(KlassForArrays, List(notComponentType))
        case Some(others) =>
          throw new IllegalArgumentException(
            s"Array generic parameters: $others doesn't match parameters from component type: ${klass.getComponentType}"
          )
      }
    }

    private def determineStandardClassType(klass: Class[_], parametersOpt: Option[List[TypingResult]]): TypedClass =
      parametersOpt match {
        case None =>
          TypedClass(klass, klass.getTypeParameters.map(_ => Unknown).toList)
        case Some(params) if params.size != klass.getTypeParameters.size =>
          val className     = klass.getSimpleName
          val paramGiven    = s"$className[${params.map(_.display).mkString(", ")}]"
          val paramExpected = s"$className[${klass.getTypeParameters.map(_.getName).mkString(", ")}]"
          throw new IllegalArgumentException(
            s"Type's generic parameters don't match expected type parameters: found $paramGiven, expected $paramExpected. This may be caused by passing incorrect arguments or using type aliases."
          )
        case Some(params) =>
          TypedClass(klass, params)
      }

    def typedNull: TypingResult = TypedNull

    def taggedDictValue(typ: SingleTypingResult, dictId: String): TypedTaggedValue = tagged(typ, s"dictValue:$dictId")

    def tagged(typ: SingleTypingResult, tag: String): TypedTaggedValue = TypedTaggedValue(typ, tag)

    def fromInstance(obj: Any): TypingResult = {
      obj match {
        case null =>
          TypedNull
        case map: Map[String @unchecked, _] =>
          val fieldTypes = typeMapFields(map)
          TypedObjectTypingResult(fieldTypes, mapBasedRecordUnderlyingType[Map[_, _]](fieldTypes))
        case javaMap: java.util.Map[String @unchecked, _] =>
          val fieldTypes = typeMapFields(javaMap.asScala.toMap)
          TypedObjectTypingResult(fieldTypes)
        case list: List[_] =>
          genericTypeClass(classOf[List[_]], List(supertypeOfElementTypes(list)))
        case javaList: java.util.List[_] =>
          genericTypeClass(classOf[java.util.List[_]], List(supertypeOfElementTypes(javaList.asScala.toList)))
        case typeFromInstance: TypedFromInstance => typeFromInstance.typingResult
        case other =>
          Typed(other.getClass) match {
            case typedClass: TypedClass =>
              SimpleObjectEncoder.encode(typedClass, other) match {
                case Valid(_)   => TypedObjectWithValue(typedClass, other)
                case Invalid(_) => typedClass
              }
            case notTypedClass => notTypedClass
          }

      }
    }

    private def typeMapFields(map: Map[String, Any]) = map.map { case (k, v) =>
      k -> fromInstance(v)
    }

    // This is a factory method allowing to create TypedUnion, but also ensuring that none of type will be duplicated,
    // TypedNull will be omitted etc. If you want to create "empty" type, use typedNull.
    def apply(firstType: TypingResult, secondType: TypingResult, restOfTypes: TypingResult*): TypingResult = {
      apply(NonEmptyList(firstType, secondType :: restOfTypes.toList))
    }

    // This method returns TypeNull for empty possibleTypes - it should be used carefully as TypedNull is in most
    // cases not usable (only literal null value can satisfy this type)
    def fromIterableOrNullType(possibleTypes: Iterable[TypingResult]): TypingResult = {
      NonEmptyList.fromList(possibleTypes.toList).map(Typed(_)).getOrElse(TypedNull)
    }

    // Creates Typed representation of sum of possible types. We don't want to allow to pass a normal List because
    // We want to consciously decide how to handle this corner case - sometimes someone want to have a TypedNull in this case,
    // and sometimes just to skip this element
    def apply(possibleTypes: NonEmptyList[TypingResult]): TypingResult = {
      // We use local function instead of lambda to get compilation error
      // when some type is not handled.
      def flattenType(t: TypingResult): Option[Set[SingleTypingResult]] = t match {
        case Unknown                    => None
        case TypedNull                  => Some(Set.empty)
        case single: SingleTypingResult => Some(Set(single))
        case union: TypedUnion          => Some(union.possibleTypes.toList.toSet)
      }

      val flattenedTypes = possibleTypes.toList.map(flattenType).sequence.map(_.flatten.distinct)
      flattenedTypes match {
        case None                          => Unknown
        case Some(Nil)                     => TypedNull
        case Some(single :: Nil)           => single
        case Some(first :: second :: rest) => new TypedUnion(first, second, rest)
      }
    }

  }

  private[api] def mapBasedRecordUnderlyingType[T: ClassTag](fields: Map[String, TypingResult]): TypedClass = {
    val valueType = superTypeOfTypes(fields.values)
    Typed.genericTypeClass[T](List(Typed[String], valueType))
  }

  private def supertypeOfElementTypes(list: List[_]): TypingResult = {
    superTypeOfTypes(list.map(fromInstance))
  }

  private def superTypeOfTypes(list: Iterable[TypingResult]) = {
    val superTypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.AnySuperclass, true)
    list
      .reduceOption(superTypeFinder.commonSupertype(_, _)(NumberTypesPromotionStrategy.ToSupertype))
      .getOrElse(Unknown)
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

  trait TypedFromInstance {
    def typingResult: TypingResult
  }

  case class CastTypedValue[T: TypeTag]() {

    def unapply(typingResult: TypingResult): Option[TypingResultTypedValue[T]] = {
      Option(typingResult).filter(_.canBeSubclassOf(Typed.fromDetailedType[T])).map(new TypingResultTypedValue(_))
    }

  }

  class TypingResultTypedValue[T](typingResult: TypingResult) {
    def valueOpt: Option[T] = typingResult.valueOpt.asInstanceOf[Option[T]]
  }

}
