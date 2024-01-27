package pl.touk.nussknacker.engine.api.typed.supertype

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder.SupertypeClassResolutionStrategy
import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class finding common supertype of two types. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  *
  * This class, like CanBeSubclassDeterminer is in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  */
class CommonSupertypeFinder private (classResolutionStrategy: SupertypeClassResolutionStrategy) {

  def commonSupertype(left: TypingResult, right: TypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): TypingResult = {
    (left, right) match {
      case (Unknown, _)   => Unknown // can't be sure intention of user - union is more secure than intersection
      case (_, Unknown)   => Unknown
      case (TypedNull, r) => commonSupertypeWithNull(r)
      case (r, TypedNull) => commonSupertypeWithNull(r)
      case (l: SingleTypingResult, r: TypedUnion)         => Typed(commonSupertype(Set(l), r.possibleTypes))
      case (l: TypedUnion, r: SingleTypingResult)         => Typed(commonSupertype(l.possibleTypes, Set(r)))
      case (l: SingleTypingResult, r: SingleTypingResult) => singleCommonSupertype(l, r)
      case (l: TypedUnion, r: TypedUnion)                 => Typed(commonSupertype(l.possibleTypes, r.possibleTypes))
    }
  }

  private def commonSupertypeWithNull(typ: TypingResult): TypingResult = typ match {
    // TODO: Handle maps with known value.
    // Allowing null makes all information about value invalid, so it is removed
    case TypedObjectWithValue(r, _) => r
    case r                          => r
  }

  private def commonSupertype(leftSet: Set[SingleTypingResult], rightSet: Set[SingleTypingResult])(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Set[TypingResult] =
    leftSet.flatMap(l => rightSet.map(singleCommonSupertype(l, _)))

  private def singleCommonSupertype(left: SingleTypingResult, right: SingleTypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): TypingResult = {
    def fallback = classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.WithFallbackToObjType => klassCommonSupertype(left.objType, right.objType)
      case _                                                      => Typed.empty
    }
    (left, right) match {
      case (l: TypedObjectTypingResult, r: TypedObjectTypingResult) =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType)
            .map { commonSupertype =>
              // can't be sure intention of user - union of fields is more secure than intersection
              val fields = unionOfFields(l, r)
              TypedObjectTypingResult(fields, commonSupertype)
            }
            .getOrElse(fallback)
        }
      case (_: TypedObjectTypingResult, _) => fallback
      case (_, _: TypedObjectTypingResult) => fallback
      case (l: TypedDict, r: TypedDict) if l.dictId == r.dictId =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType)
            .map { _ =>
              l // should we recognize static vs dynamic and compute some union?
            }
            .getOrElse(fallback)
        }
      case (_: TypedDict, _) => fallback
      case (_, _: TypedDict) => fallback

      case (l @ TypedTaggedValue(leftType, leftTag), r @ TypedTaggedValue(rightType, rightTag))
          if leftTag == rightTag =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          Option(singleCommonSupertype(leftType, rightType))
            .collect { case single: SingleTypingResult =>
              TypedTaggedValue(single, leftTag)
            }
            .getOrElse(fallback)
        }
      case (TypedObjectWithValue(leftType, leftValue), TypedObjectWithValue(rightType, rightValue))
          if leftValue == rightValue =>
        klassCommonSupertype(leftType, rightType) match {
          case typedClass: TypedClass => TypedObjectWithValue(typedClass, leftValue)
          case other                  => other
        }
      case (_: TypedTaggedValue, _)    => fallback
      case (l: TypedObjectWithData, r) => singleCommonSupertype(l.underlying, r)
      case (_, _: TypedTaggedValue)    => fallback
      case (l, r: TypedObjectWithData) => singleCommonSupertype(l, r.underlying)

      case (f: TypedClass, s: TypedClass) => klassCommonSupertype(f, s)
    }
  }

  private def checkDirectEqualityOrMorePreciseCommonSupertype[T <: SingleTypingResult](left: T, right: T)(
      preciseCommonSupertype: => TypingResult
  ) = {
    if (left == right) {
      left
    } else {
      preciseCommonSupertype
    }
  }

  private def unionOfFields(l: TypedObjectTypingResult, r: TypedObjectTypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Map[String, TypingResult] =
    (l.fields.toList ++ r.fields.toList)
      .groupBy(_._1)
      .map { case (key, value) => key -> value.map(_._2) }
      .flatMap {
        case (fieldName, leftType :: rightType :: Nil) =>
          val common = commonSupertype(leftType, rightType)
          if (common == Typed.empty)
            None // fields type collision - skipping this field
          else
            Some(fieldName -> common)
        case (fieldName, singleType :: Nil) =>
          Some(fieldName -> singleType)
        case (_, longerList) =>
          throw new IllegalArgumentException(
            "Computing union of more than two fields: " + longerList
          ) // shouldn't happen
      }

  // This implementation is because TypedObjectTypingResult has underlying TypedClass instead of TypingResult
  private def klassCommonSupertypeReturningTypedClass(left: TypedClass, right: TypedClass)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Option[TypedClass] = {
    val boxedLeftClass  = ClassUtils.primitiveToWrapper(left.klass)
    val boxedRightClass = ClassUtils.primitiveToWrapper(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(classOf[Number].isAssignableFrom)) {
      numberPromotionStrategy.promoteClasses(boxedLeftClass, boxedRightClass) match {
        case tc: TypedClass => Some(tc)
        case _              => Some(Typed.typedClass[Number])
      }
    } else {
      val forComplexTypes = commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params)
      forComplexTypes match {
        case tc: TypedClass => Some(tc)
        case _              => None // empty, union and so on
      }
    }
  }

  private def klassCommonSupertype(left: TypedClass, right: TypedClass)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): TypingResult = {
    val boxedLeftClass  = ClassUtils.primitiveToWrapper(left.klass)
    val boxedRightClass = ClassUtils.primitiveToWrapper(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(classOf[Number].isAssignableFrom)) {
      numberPromotionStrategy.promoteClasses(boxedLeftClass, boxedRightClass)
    } else {
      commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params)
    }
  }

  private def commonSuperTypeForComplexTypes(
      left: Class[_],
      leftParams: List[TypingResult],
      right: Class[_],
      rightParams: List[TypingResult]
  ) = {
    if (left.isAssignableFrom(right)) {
      genericClassWithSuperTypeParams(left, leftParams, rightParams)
    } else if (right.isAssignableFrom(left)) {
      genericClassWithSuperTypeParams(right, rightParams, leftParams)
    } else {
      // until here things are rather simple
      commonSuperTypeForClassesNotInSameInheritanceLine(left, right)
    }
  }

  private def genericClassWithSuperTypeParams(
      superType: Class[_],
      superTypeParams: List[TypingResult],
      subTypeParams: List[TypingResult]
  ) = {
    // Here is a little bit heuristics. We are not sure what generic types we are comparing, for List[T] with Collection[U]
    // it is ok to look for common super type of T and U but for Comparable[T] and Integer it won't be ok.
    // Maybe we should do this common super type checking only for well known cases?
    val commonSuperTypesForGenericParams = if (superTypeParams.size == subTypeParams.size) {
      // For generic params, in case of not matching classes, it is better to return Unknown than Typed.empty,
      // but we still want to return the precise type - e.g. available fields in records
      superTypeParams.zip(subTypeParams).map { case (l, p) =>
        CommonSupertypeFinder.FallbackToObjectType.commonSupertype(l, p)
      }
    } else {
      superTypeParams
    }
    Typed.genericTypeClass(superType, commonSuperTypesForGenericParams)
  }

  private def commonSuperTypeForClassesNotInSameInheritanceLine(left: Class[_], right: Class[_]): TypingResult = {
    classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.Intersection =>
        Typed(ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right).map(Typed(_)))
      case SupertypeClassResolutionStrategy.WithFallbackToObjType =>
        val result = Typed(ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right).map(Typed(_)))
        if (result == Typed.empty) Unknown else result
      case SupertypeClassResolutionStrategy.Union =>
        Typed(Set(left, right).map(Typed(_)))
    }
  }

}

object CommonSupertypeFinder {

  // It is default, it always looks for an intersection of classes. If doesn't find it, returns Typed.empty
  val Intersection = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Intersection)

  // This finder in general shouldn't be used. It returns union of types when no matching classes find
  // For other cases it behave like Intersection
  val Union = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Union)

  // This finder has fallback to object type (SingleTypingResult.objType). When no matching super type class found,
  // it returns Any (which currently is expressed as Unknown). It is useful when you are looking for some object type's
  // class e.g. for generic parameters and precise super type is only nice-to-have not a blocker
  object FallbackToObjectType {
    private val delegate = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.WithFallbackToObjType)

    def commonSupertype(left: TypingResult, right: TypingResult): TypingResult = {
      delegate.commonSupertype(left, right)(NumberTypesPromotionStrategy.ToSupertype)
    }

  }

  private sealed trait SupertypeClassResolutionStrategy

  private object SupertypeClassResolutionStrategy {

    case object Intersection extends SupertypeClassResolutionStrategy

    case object Union extends SupertypeClassResolutionStrategy

    case object WithFallbackToObjType extends SupertypeClassResolutionStrategy

  }

}
