package pl.touk.nussknacker.engine.api.typed.supertype

import cats.data.NonEmptyList
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class finding common supertype of two types. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  *
  * This class, like CanBeSubclassDeterminer is in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  *
  * TODO: strictTaggedTypesChecking was added as quickFix for compare Type with TaggedType. We should remove it after we will support creating model with TaggedType field
  */
class CommonSupertypeFinder(
    classResolutionStrategy: SupertypeClassResolutionStrategy,
    strictTaggedTypesChecking: Boolean
) {

  def commonSupertype(left: TypingResult, right: TypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): TypingResult =
    (left, right) match {
      case (Unknown, _)   => Unknown // can't be sure intention of user - union is more secure than intersection
      case (_, Unknown)   => Unknown
      case (TypedNull, r) => commonSupertypeWithNull(r)
      case (r, TypedNull) => commonSupertypeWithNull(r)
      case (l: SingleTypingResult, r: TypedUnion) => Typed(commonSupertype(NonEmptyList(l, Nil), r.possibleTypes))
      case (l: TypedUnion, r: SingleTypingResult) => Typed(commonSupertype(l.possibleTypes, NonEmptyList(r, Nil)))
      case (l: SingleTypingResult, r: SingleTypingResult) => singleCommonSupertype(l, r)
      case (l: TypedUnion, r: TypedUnion)                 => Typed(commonSupertype(l.possibleTypes, r.possibleTypes))
    }

  private def commonSupertypeWithNull(typ: TypingResult): TypingResult = typ match {
    // TODO: Handle maps with known value.
    // Allowing null makes all information about value invalid, so it is removed
    case TypedObjectWithValue(r, _) => r
    case r                          => r
  }

  private def commonSupertype(leftSet: NonEmptyList[SingleTypingResult], rightSet: NonEmptyList[SingleTypingResult])(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): NonEmptyList[TypingResult] =
    leftSet.flatMap(l => rightSet.map(singleCommonSupertype(l, _)))

  private def singleCommonSupertype(left: SingleTypingResult, right: SingleTypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): TypingResult =
    (left, right) match {
      case (l: TypedObjectTypingResult, r: TypedObjectTypingResult) =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType)
            .map { commonSupertype =>
              // can't be sure intention of user - union of fields is more secure than intersection
              val fields = unionOfFields(l, r)
              TypedObjectTypingResult(fields, commonSupertype)
            }
            .getOrElse(Typed.typedNull)
        }
      case (_: TypedObjectTypingResult, _) => Typed.typedNull
      case (_, _: TypedObjectTypingResult) => Typed.typedNull
      case (l: TypedDict, r: TypedDict) if l.dictId == r.dictId =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType)
            .map { _ =>
              l // should we recognize static vs dynamic and compute some union?
            }
            .getOrElse(Typed.typedNull)
        }
      case (_: TypedDict, _) => Typed.typedNull
      case (_, _: TypedDict) => Typed.typedNull

      case (l @ TypedTaggedValue(leftType, leftTag), r @ TypedTaggedValue(rightType, rightTag))
          if leftTag == rightTag =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          Option(singleCommonSupertype(leftType, rightType))
            .collect { case single: SingleTypingResult =>
              TypedTaggedValue(single, leftTag)
            }
            .getOrElse(Typed.typedNull)
        }
      case (TypedObjectWithValue(leftType, leftValue), TypedObjectWithValue(rightType, rightValue))
          if leftValue == rightValue =>
        klassCommonSupertype(leftType, rightType) match {
          case typedClass: TypedClass => TypedObjectWithValue(typedClass, leftValue)
          case other                  => other
        }
      case (_: TypedTaggedValue, _) if strictTaggedTypesChecking => Typed.typedNull
      case (l: TypedObjectWithData, r)                           => singleCommonSupertype(l.underlying, r)
      case (_, _: TypedTaggedValue) if strictTaggedTypesChecking => Typed.typedNull
      case (l, r: TypedObjectWithData)                           => singleCommonSupertype(l, r.underlying)

      case (f: TypedClass, s: TypedClass) => klassCommonSupertype(f, s)
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
          if (common == Typed.typedNull)
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
    if (List(boxedLeftClass, boxedRightClass).forall(isSimpleType)) {
      commonSuperTypeForSimpleTypes(boxedLeftClass, boxedRightClass) match {
        case tc: TypedClass => Some(tc)
        case union: TypedUnion if union.possibleTypes.forall(_.canBeSubclassOf(Typed[Number])) =>
          Some(Typed.typedClass[Number])
        case _ => None // empty e.g. conflicting simple types
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
    if (List(boxedLeftClass, boxedRightClass).forall(isSimpleType)) {
      commonSuperTypeForSimpleTypes(boxedLeftClass, boxedRightClass)
    } else {
      commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params)
    }
  }

  private def commonSuperTypeForSimpleTypes(left: Class[_], right: Class[_])(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): TypingResult = {
    if (classOf[Number].isAssignableFrom(left) && classOf[Number].isAssignableFrom(right))
      numberPromotionStrategy.promoteClasses(left, right)
    else if (left == right)
      Typed(left)
    else
      classResolutionStrategy match {
        case SupertypeClassResolutionStrategy.AnySuperclass => Unknown
        case _                                              => Typed.typedNull
      }
  }

  private def commonSuperTypeForComplexTypes(
      left: Class[_],
      leftParams: List[TypingResult],
      right: Class[_],
      rightParams: List[TypingResult]
  )(implicit numberPromotionStrategy: NumberTypesPromotionStrategy) = {
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
  )(implicit numberPromotionStrategy: NumberTypesPromotionStrategy) = {
    // Here is a little bit heuristics. We are not sure what generic types we are comparing, for List[T] with Collection[U]
    // it is ok to look for common super type of T and U but for Comparable[T] and Integer it won't be ok.
    // Maybe we should do this common super type checking only for well known cases?
    val commonSuperTypesForGenericParams = if (superTypeParams.size == subTypeParams.size) {
      // for generic params it is always better to return Union generic param than Typed.empty
      val anyFinder =
        new CommonSupertypeFinder(SupertypeClassResolutionStrategy.AnySuperclass, strictTaggedTypesChecking)
      superTypeParams.zip(subTypeParams).map { case (l, p) =>
        anyFinder.commonSupertype(l, p)
      }
    } else {
      superTypeParams
    }
    Typed.genericTypeClass(superType, commonSuperTypesForGenericParams)
  }

  private def commonSuperTypeForClassesNotInSameInheritanceLine(left: Class[_], right: Class[_]): TypingResult = {
    classResolutionStrategy match {
      // TODO: Should we split null case from nothing? Currently null == null is not valid spell expression
      case SupertypeClassResolutionStrategy.Intersection =>
        Typed.fromIterableOrNullType(
          ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right).map(Typed(_)).toList
        )
      case SupertypeClassResolutionStrategy.AnySuperclass =>
        Typed.fromIterableOrNullType(
          ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right).map(Typed(_)).toList
        ) match {
          case TypedNull => Unknown
          case other     => other
        }
      case SupertypeClassResolutionStrategy.Union =>
        Typed(NonEmptyList(left, right :: Nil).map(Typed(_)))
    }
  }

  private def isSimpleType(clazz: Class[_]) =
    clazz == classOf[java.lang.Boolean] || clazz == classOf[String] || classOf[Number].isAssignableFrom(clazz)

}

sealed trait SupertypeClassResolutionStrategy

object SupertypeClassResolutionStrategy {

  case object Intersection extends SupertypeClassResolutionStrategy

  case object Union extends SupertypeClassResolutionStrategy

  case object AnySuperclass extends SupertypeClassResolutionStrategy

}
