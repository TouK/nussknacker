package pl.touk.nussknacker.engine.util.functions

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.springframework.util.{NumberUtils => SpringNumberUtils}
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, GenericType, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.ForLargeNumbersOperation
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{
  Typed,
  TypedClass,
  TypedObjectTypingResult,
  TypedObjectWithValue,
  Unknown
}
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

import java.util.{Collections, Objects}
import scala.jdk.CollectionConverters._
import scala.language.higherKinds
import scala.reflect.ClassTag

object collection extends CollectionUtils

trait CollectionUtils extends HideToString {

  import CollectionUtils._

  @Documentation(description = "Concatenates two lists")
  @GenericType(typingFunction = classOf[ListAdditionTyping])
  def concat[T](
      @ParamName("list1") list1: java.util.List[T],
      @ParamName("list2") list2: java.util.List[T]
  ): java.util.List[T] =
    (list1.asScala.toList ++ list2.asScala).asJava

  @Documentation(description =
    "Merges two maps. Values in the first map will be overwritten with values from the second map if keys are the same"
  )
  @GenericType(typingFunction = classOf[MapMergeTyping])
  def merge[K, V](
      @ParamName("map1") map1: java.util.Map[K, V],
      @ParamName("map2") map2: java.util.Map[K, V]
  ): java.util.Map[K, V] = {
    val merged = new java.util.HashMap[K, V](map1.size() + map2.size())
    merged.putAll(map1)
    merged.putAll(map2)
    merged
  }

  @Documentation(description = "Returns the smallest element (elements must be comparable)")
  @GenericType(typingFunction = classOf[ListElementTyping])
  def min[T <: Comparable[T]](@ParamName("list") list: java.util.Collection[T]): T = Collections.min[T](list)

  @Documentation(description = "Returns the largest element (elements must be comparable)")
  @GenericType(typingFunction = classOf[ListElementTyping])
  def max[T <: Comparable[T]](@ParamName("list") list: java.util.Collection[T]): T = Collections.max[T](list)

  @Documentation(description =
    "Returns a slice of the list starting with start index (inclusive) and ending at stop index (exclusive)"
  )
  @GenericType(typingFunction = classOf[ListTyping])
  def slice[T](
      @ParamName("list") list: java.util.Collection[T],
      @ParamName("start") start: Int,
      @ParamName("stop") stop: Int
  ): java.util.List[T] =
    list.asScala.slice(start, stop).toList.asJava

  // TODO: This method currently has the following limitations:
  // * it won't return the expected type for empty list
  // * it works slower than it could with statically typed result
  // To overcome this limitations we should supply returnType to this method
  @Documentation(description = "Returns a sum of all elements")
  @GenericType(typingFunction = classOf[ListElementTypingForSum])
  def sum[T <: java.lang.Number](@ParamName("listOfNumbers") list: java.util.Collection[T]): T = {
    val types = list.asScala.map(Typed.fromInstance(_)).toList
    val targetType = types match {
      // We are not able to determine the expected type for an empty list
      case Nil      => return 0.0.asInstanceOf[T]
      case t :: Nil => ForLargeNumbersOperation.promoteSingle(t)
      case l        => l.reduce((a, b) => ForLargeNumbersOperation.promote(a, b))
    }

    if (targetType == Typed[java.lang.Long]) {
      list.asScala.map(_.longValue()).sum.asInstanceOf[T]
    } else if (targetType == Typed[java.lang.Double]) {
      list.asScala.map(_.doubleValue()).sum.asInstanceOf[T]
    } else if (targetType == Typed[java.math.BigInteger]) {
      list.asScala
        .map(SpringNumberUtils.convertNumberToTargetClass(_, classOf[java.math.BigInteger]))
        .reduce((a, b) => a.add(b))
        .asInstanceOf[T]
    } else {
      list.asScala
        .map(SpringNumberUtils.convertNumberToTargetClass(_, classOf[java.math.BigDecimal]))
        .reduce((a, b) => a.add(b))
        .asInstanceOf[T]
    }
  }

  @Documentation(description = "Returns a list of all elements sorted in ascending order (elements must be comparable)")
  @GenericType(typingFunction = classOf[ListTyping])
  def sortedAsc[T <: Comparable[T]](@ParamName("list") list: java.util.Collection[T]): java.util.List[T] =
    sorted(list, desc = false)

  @Documentation(description =
    "Returns a list of all elements sorted in descending order (elements must be comparable)"
  )
  @GenericType(typingFunction = classOf[ListTyping])
  def sortedDesc[T <: Comparable[T]](@ParamName("list") list: java.util.Collection[T]): java.util.List[T] =
    sorted(list, desc = true)

  private def sorted[T <: Comparable[T]](list: java.util.Collection[T], desc: Boolean): java.util.List[T] = {
    checkIfComparable(list)
    val values = new java.util.ArrayList[T](list)
    Collections.sort[T](values)
    if (desc) {
      Collections.reverse(values)
    }
    values
  }

  @Documentation(description =
    "Returns a list of all elements sorted by record field in ascending order (elements must be comparable)"
  )
  @GenericType(typingFunction = classOf[RecordCollectionSortingTyping])
  def sortedAscBy(
      @ParamName("list") list: java.util.Collection[java.util.Map[String, Any]],
      @ParamName("fieldName") fieldName: String
  ): java.util.List[java.util.Map[String, Any]] = {
    checkIfNotNull(fieldName, "fieldName")
    list.asScala.toList.sortWith { (firstMap, secondMap) =>
      (firstMap.get(fieldName), secondMap.get(fieldName)) match {
        case (a, b) if a != null && b != null && a.getClass == b.getClass && a.isInstanceOf[Comparable[_]] =>
          a.asInstanceOf[Comparable[Any]].compareTo(b.asInstanceOf[Comparable[Any]]) < 0
        case (a, b) if a == null && b != null => true
        case (a, b) if a != null && b == null => false
        case (a, b) if a == null && b == null => false
        case _                                => throw new IllegalArgumentException("Elements cannot be compared")
      }
    }.asJava
  }

  @Documentation(description = "Returns a list that contains elements in reversed order from the given list")
  @GenericType(typingFunction = classOf[ListTyping])
  def reverse(@ParamName("list") list: java.util.List[_]): java.util.List[_] = {
    val result = new java.util.ArrayList(list)
    Collections.reverse(result)
    result
  }

  @Documentation(description = "Returns a list made of first n elements of the given list")
  @GenericType(typingFunction = classOf[ListTyping])
  def take[T](@ParamName("list") list: java.util.List[T], @ParamName("max") max: Int): java.util.List[T] =
    list.asScala.take(max).asJava

  @Documentation(description = "Returns a list made of last n elements of the given list")
  @GenericType(typingFunction = classOf[ListTyping])
  def takeLast[T](@ParamName("list") list: java.util.List[T], @ParamName("max") max: Int): java.util.List[T] =
    list.asScala.takeRight(max).asJava

  @Documentation(description = "Creates a string made of all elements of the list separated with the given separator")
  def join[T](@ParamName("list") list: java.util.List[T], @ParamName("separator") separator: String): String =
    String.join(separator, list.asScala.map(Objects.toString).asJava)

  @Documentation(description =
    "Cross joins two lists of maps: eg. product({{a: 'a'},{b: 'b'}}, {{c: 'c'},{d: 'd'}}) => {{a: 'a',c: 'c'},{b: 'b',c: 'c'},{a: 'a',d: 'd'},{b: 'b',d: 'd'}}"
  )
  def product[K, V](
      list1: java.util.List[java.util.Map[K, V]],
      list2: java.util.List[java.util.Map[K, V]]
  ): java.util.List[java.util.Map[K, V]] = {
    val l1 = list1.asScala.map(_.asScala)
    val l2 = list2.asScala.map(_.asScala)
    val res = for {
      m1 <- l1
      m2 <- l2
    } yield m1 ++ m2
    res.map(_.asJava).asJava
  }

  @Documentation(description =
    "Returns a list that contains all elements contained in list1, that don't appear in list2"
  )
  @GenericType(typingFunction = classOf[ListTyping])
  def diff[T](
      @ParamName("list1") list1: java.util.List[T],
      @ParamName("list2") list2: java.util.List[T]
  ): java.util.List[T] =
    list1.asScala.filterNot(list2.asScala.toSet).asJava

  @Documentation(description =
    "Returns a list that contains all unique elements that are contained by both list1 and list2"
  )
  @GenericType(typingFunction = classOf[ListTyping])
  def intersect[T](
      @ParamName("list1") list1: java.util.List[T],
      @ParamName("list2") list2: java.util.List[T]
  ): java.util.List[T] =
    (list1.asScala.toSet intersect list2.asScala.toSet).toList.asJava

  @Documentation(description = "Returns a list that contains unique elements from the given list")
  @GenericType(typingFunction = classOf[ListTyping])
  def distinct[T](@ParamName("list") list: java.util.List[T]): java.util.List[T] =
    new java.util.ArrayList(new java.util.HashSet[T](list))

  @Documentation(description = "Returns a copy of the list with its elements shuffled")
  @GenericType(typingFunction = classOf[ListTyping])
  def shuffle[T](@ParamName("list") list: java.util.Collection[T]): java.util.List[T] = {
    val values = new java.util.ArrayList[T](list)
    Collections.shuffle(values)
    values
  }

  @Documentation(description = "Returns a list of all elements from all lists in the given list")
  @GenericType(typingFunction = classOf[ListElementTyping])
  def flatten[T](@ParamName("list") list: java.util.Collection[java.util.Collection[T]]): java.util.List[T] =
    list.asScala.flatMap(_.asScala).toList.asJava

  private def checkIfComparable(list: java.util.Collection[_]): Unit =
    list.asScala.foreach(checkIfComparable)

  private def checkIfComparable(element: Any): Unit =
    if (!element.isInstanceOf[Comparable[_]]) {
      throw new java.lang.ClassCastException("Provided value is not comparable: " + element)
    }

  private def checkIfNotNull[T](t: T, fieldName: String): Unit =
    if (t == null) {
      throw new IllegalArgumentException(s"Provided '$fieldName' cannot be null")
    }

}

object CollectionUtils {
  private val unknownMapType = Typed.fromDetailedType[java.util.Map[Any, Any]]
  private val numberType     = Typed.fromDetailedType[java.lang.Number]

  class CollectionTyping[F[_]](implicit classTag: ClassTag[F[_]]) extends TypingFunction {
    private val fClass: Class[F[_]] = classTag.runtimeClass.asInstanceOf[Class[F[_]]]

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
      case (f @ TypedClass(`fClass`, element :: Nil)) :: _ => f.copy(params = element.withoutValue :: Nil).validNel
      case TypedObjectWithValue(f @ TypedClass(`fClass`, element :: Nil), _) :: _ =>
        f.copy(params = element.withoutValue :: Nil).validNel
      case firstArgument :: _ => firstArgument.validNel
      case _                  => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

  class CollectionElementTyping[F[_]](implicit classTag: ClassTag[F[_]]) extends TypingFunction {
    private val fClass: Class[F[_]] = classTag.runtimeClass.asInstanceOf[Class[F[_]]]

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
      case TypedClass(`fClass`, componentType :: Nil) :: _ => componentType.withoutValue.validNel
      case TypedObjectWithValue(TypedClass(`fClass`, componentType :: Nil), _) :: _ =>
        componentType.withoutValue.validNel
      case firstArgument :: _ => firstArgument.withoutValue.validNel
      case _                  => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

  class CollectionElementTypingForSum[F[_]](implicit classTag: ClassTag[F[_]]) extends CollectionElementTyping[F] {

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = {
      super.computeResultType(arguments).map { elementType =>
        if (elementType == Typed[java.lang.Number]) {
          // If it is a Number, leave it as is, we will check exact types in runtime
          elementType
        } else {
          ForLargeNumbersOperation.promoteSingle(elementType)
        }
      }
    }

  }

  class CollectionMergeTyping[F[_]](implicit classTag: ClassTag[F[_]]) extends TypingFunction {
    private val fClass: Class[F[_]] = classTag.runtimeClass.asInstanceOf[Class[F[_]]]

    private def commonFieldHasTheSameType(
        fields1: Map[String, typing.TypingResult],
        fields2: Map[String, typing.TypingResult]
    ) = {
      val commonFields = fields1.keys.toSet intersect fields2.keys.toSet
      fields1.filter { case (key, _) => commonFields.contains(key) }.map { case (key, value) =>
        key -> value.withoutValue
      } ==
        fields2.filter { case (key, _) => commonFields.contains(key) }.map { case (key, value) =>
          key -> value.withoutValue
        }
    }

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
      case (list1 @ TypedClass(`fClass`, _ :: Nil)) ::
          (list2 @ TypedClass(`fClass`, _ :: Nil)) :: Nil =>
        concatType(list1, list2)

      case (list1 @ TypedClass(`fClass`, _ :: Nil)) ::
          TypedObjectWithValue(list2 @ TypedClass(`fClass`, _ :: Nil), _) :: Nil =>
        concatType(list1, list2)

      case TypedObjectWithValue(list1 @ TypedClass(`fClass`, _ :: Nil), _) ::
          (list2 @ TypedClass(`fClass`, _ :: Nil)) :: Nil =>
        concatType(list1, list2)

      case TypedObjectWithValue(list1 @ TypedClass(`fClass`, _ :: Nil), _) ::
          TypedObjectWithValue(list2 @ TypedClass(`fClass`, _ :: Nil), _) :: Nil =>
        concatType(list1, list2)

      case _ => Typed.genericTypeClass(fClass, List(Unknown)).validNel
    }

    private def concatType(list1: TypedClass, list2: TypedClass) = (list1, list2) match {
      case (
            listType @ TypedClass(`fClass`, firstComponentType :: Nil),
            TypedClass(`fClass`, secondComponentType :: Nil)
          ) =>
        (firstComponentType, secondComponentType) match {
          case (TypedObjectTypingResult(x, _, infoX), TypedObjectTypingResult(y, _, infoY))
              if commonFieldHasTheSameType(x, y) =>
            listType
              .copy(params =
                Typed.record(
                  x.view.map { case (key, value) => key -> value.withoutValue } ++ y.view.map { case (key, value) =>
                    key -> value.withoutValue
                  },
                  Typed.typedClass[java.util.HashMap[_, _]],
                  infoX ++ infoY
                ) :: Nil
              )
              .validNel
          case (_: TypedObjectTypingResult, _: TypedObjectTypingResult) =>
            listType.copy(params = Unknown :: Nil).validNel
          case (`unknownMapType`, _: TypedObjectTypingResult) | (_: TypedObjectTypingResult, `unknownMapType`) |
              (`unknownMapType`, `unknownMapType`) =>
            listType.copy(params = unknownMapType :: Nil).validNel
          case _ if firstComponentType.withoutValue == secondComponentType.withoutValue =>
            listType.copy(params = firstComponentType.withoutValue :: Nil).validNel
          case _ if firstComponentType.canBeSubclassOf(numberType) && secondComponentType.canBeSubclassOf(numberType) =>
            Typed.genericTypeClass(fClass, List(numberType)).validNel
          case _ => listType.copy(params = Unknown :: Nil).validNel
        }
      case _ => Typed.genericTypeClass(fClass, List(Unknown)).validNel
    }

  }

  class MapMergeTyping extends TypingFunction {

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
      case TypedObjectTypingResult(x, _, infoX) :: TypedObjectTypingResult(y, _, infoY) :: Nil =>
        Typed.record(x ++ y, Typed.typedClass[java.util.HashMap[_, _]], infoX ++ infoY).validNel
      case (typedClass: TypedClass) :: _      => typedClass.validNel
      case _ :: (typedClass: TypedClass) :: _ => typedClass.validNel
      case _                                  => unknownMapType.validNel
    }

  }

  class ListTyping extends CollectionTyping[java.util.List]

  class ListAdditionTyping extends CollectionMergeTyping[java.util.List]

  class ListElementTyping extends CollectionElementTyping[java.util.List]

  class ListElementTypingForSum extends CollectionElementTypingForSum[java.util.List]

  class RecordCollectionSortingTyping extends TypingFunction {
    private val listClass       = classOf[java.util.List[java.util.Map[String, Any]]]
    private val fieldClass      = classOf[String]
    private val comparableClass = classOf[Comparable[Any]]

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = {
      arguments match {
        case (f @ TypedClass(`listClass`, (e @ TypedObjectTypingResult(fields, _, _)) :: Nil))
            :: TypedObjectWithValue(TypedClass(`fieldClass`, Nil), fieldName) :: _ =>
          listResultType(f, e, fields, fieldName)
        case TypedObjectWithValue(f @ TypedClass(`listClass`, (e @ TypedObjectTypingResult(fields, _, _)) :: Nil), _) ::
            TypedObjectWithValue(TypedClass(`fieldClass`, Nil), fieldName) :: _ =>
          listResultType(f, e, fields, fieldName)
        case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
      }
    }

    private def listResultType(
        baseTypeClass: TypedClass,
        parametersTypes: TypedObjectTypingResult,
        fields: Map[String, typing.TypingResult],
        fieldName: Any
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = {
      fields.get(fieldName.asInstanceOf[String]) match {
        case Some(TypedClass(klass, _)) if comparableClass.isAssignableFrom(klass) =>
          baseTypeClass.copy(params = parametersTypes.withoutValue :: Nil).validNel
        case Some(t @ (TypedClass(_, _) | Unknown)) =>
          GenericFunctionTypingError
            .OtherError(
              s"Field: $fieldName of the type: ${t.display} isn't comparable (doesn't implement the " +
                s"Comparable interface) and cannot be used for sorting purposes."
            )
            .invalidNel
        case _ =>
          GenericFunctionTypingError
            .OtherError(
              s"Type: ${parametersTypes.display} doesn't contain field: $fieldName."
            )
            .invalidNel
      }
    }

  }

}
