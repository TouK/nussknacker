package pl.touk.nussknacker.engine.api.typed.supertype

import cats.data.NonEmptyList
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

  // It returns None if none supertype found (it is possible only for SupertypeClassResolutionStrategy.Intersection)
  // see commonSuperTypeForClassesNotInSameInheritanceLine and fallback in singleCommonSupertype
  def commonSupertypeOpt(left: TypingResult, right: TypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Option[TypingResult] = {
    (left, right) match {
      case (Unknown, _)   => Some(Unknown) // can't be sure intention of user - union is more secure than intersection
      case (_, Unknown)   => Some(Unknown)
      case (TypedNull, r) => Some(commonSupertypeWithNull(r))
      case (r, TypedNull) => Some(commonSupertypeWithNull(r))
      case (l: SingleTypingResult, r: TypedUnion) =>
        commonSupertype(NonEmptyList(l, Nil), r.possibleTypes).map(Typed(_))
      case (l: TypedUnion, r: SingleTypingResult) =>
        commonSupertype(l.possibleTypes, NonEmptyList(r, Nil)).map(Typed(_))
      case (l: SingleTypingResult, r: SingleTypingResult) => singleCommonSupertype(l, r)
      case (l: TypedUnion, r: TypedUnion) => commonSupertype(l.possibleTypes, r.possibleTypes).map(Typed(_))
    }
  }

  private def commonSupertypeWithNull(typ: TypingResult): TypingResult = typ match {
    // TODO: Handle maps with known value.
    // Allowing null makes all information about value invalid, so it is removed
    case TypedObjectWithValue(r, _) => r
    case r                          => r
  }

  // Below method is a heuristics. We don't know which types match with each another.
  // It can generate a lot of combinations for unions, some of then will be reduced inside Typed.apply(nel)
  // TODO: Do this smarter - a little step towards this would be smarter folding of types inside Typed.apply(nel) - see comment there
  //       Another thing that we can consider is not computing supertype at all (use it only for equals) and always return Union
  //       This approach will generate long types e.g. for union of records and we treat Unions lax in typing - see CanBeSubclassDeterminer.canBeSubclassOf
  //       but maybe it is not so bad
  private def commonSupertype(leftList: NonEmptyList[SingleTypingResult], rightList: NonEmptyList[SingleTypingResult])(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Option[NonEmptyList[TypingResult]] = {
    // .sequence won't do the work because it returns None if any element of list returned None
    val permutations = leftList.flatMap(l => rightList.map(singleCommonSupertype(l, _))).toList.flatten
    NonEmptyList.fromList(permutations)
  }

  private def singleCommonSupertype(left: SingleTypingResult, right: SingleTypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Option[TypingResult] = {
    def fallback = classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.FallbackToObjectType => klassCommonSupertype(left.objType, right.objType)
      case SupertypeClassResolutionStrategy.Intersection         => None
    }
    // We can't do if (left == right) left because spel promote byte and short classes to integer always so returned type for math operation will be different
    (left, right) match {
      case (f: TypedClass, s: TypedClass)                           => klassCommonSupertype(f, s)
      case (l: TypedObjectTypingResult, r: TypedObjectTypingResult) =>
        // In most cases we compare java.util.Map or GenericRecord, the only difference can be on generic params, but
        // still we'll got a class here, so this getOrElse should occur in the rare situations
        klassCommonSupertypeReturningTypedClass(l.objType, r.objType)
          .map { commonSupertype =>
            val fields = prepareFields(l, r)
            // We don't return TypeEmpty in case when fields are empty, because we can't be sure the intention of the user
            // e.g. someone can pass json object with missing field declared as optional in schema
            // and want to compare it with literal record that doesn't have this field
            TypedObjectTypingResult(fields, commonSupertype)
          }
          .orElse(fallback)
      case (l: TypedObjectTypingResult, r) => singleCommonSupertype(l.objType, r)
      case (l, r: TypedObjectTypingResult) => singleCommonSupertype(l, r.objType)
      case (TypedTaggedValue(leftType, leftTag), TypedTaggedValue(rightType, rightTag)) if leftTag == rightTag =>
        singleCommonSupertype(leftType, rightType).map {
          case single: SingleTypingResult => TypedTaggedValue(single, leftTag)
          case other                      => other
        }
      case (_: TypedTaggedValue, _) => fallback
      case (_, _: TypedTaggedValue) => fallback
      case (TypedObjectWithValue(leftType, leftValue), TypedObjectWithValue(rightType, rightValue))
          if leftValue == rightValue =>
        klassCommonSupertype(leftType, rightType).map {
          case typedClass: TypedClass => TypedObjectWithValue(typedClass, leftValue)
          case other                  => other
        }
      case (l: TypedObjectWithValue, r)           => singleCommonSupertype(l.underlying, r)
      case (l, r: TypedObjectWithValue)           => singleCommonSupertype(l, r.underlying)
      case (l: TypedDict, r: TypedDict) if l == r => Some(l)
      case (_: TypedDict, _)                      => fallback
      case (_, _: TypedDict)                      => fallback
    }
  }

  private def prepareFields(l: TypedObjectTypingResult, r: TypedObjectTypingResult)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Map[String, TypingResult] =
    (l.fields.toList ++ r.fields.toList)
      .groupBy(_._1)
      .map { case (key, value) => key -> value.map(_._2) }
      .flatMap {
        case (_, _ :: Nil) if classResolutionStrategy == SupertypeClassResolutionStrategy.Intersection =>
          None
        case (fieldName, singleType :: Nil) =>
          Some(fieldName -> singleType)
        case (fieldName, leftType :: rightType :: Nil) =>
          commonSupertypeOpt(leftType, rightType).map { common =>
            fieldName -> common
          }
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
      commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params).flatMap {
        case tc: TypedClass => Some(tc)
        case _              => None // empty, union and so on
      }
    }
  }

  private def klassCommonSupertype(left: TypedClass, right: TypedClass)(
      implicit numberPromotionStrategy: NumberTypesPromotionStrategy
  ): Option[TypingResult] = {
    val boxedLeftClass  = ClassUtils.primitiveToWrapper(left.klass)
    val boxedRightClass = ClassUtils.primitiveToWrapper(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(classOf[Number].isAssignableFrom)) {
      Some(numberPromotionStrategy.promoteClasses(boxedLeftClass, boxedRightClass))
    } else {
      commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params)
    }
  }

  private def commonSuperTypeForComplexTypes(
      left: Class[_],
      leftParams: List[TypingResult],
      right: Class[_],
      rightParams: List[TypingResult]
  ): Option[TypingResult] = {
    if (left.isAssignableFrom(right)) {
      Some(genericClassWithSuperTypeParams(left, leftParams, rightParams))
    } else if (right.isAssignableFrom(left)) {
      Some(genericClassWithSuperTypeParams(right, rightParams, leftParams))
    } else {
      // until here things are rather simple
      commonSuperTypeForClassesNotInSameInheritanceLine(left, right)
    }
  }

  private def genericClassWithSuperTypeParams(
      superType: Class[_],
      superTypeParams: List[TypingResult],
      subTypeParams: List[TypingResult]
  ): TypedClass = {
    // Here is a little bit heuristics. We are not sure what generic types we are comparing, for List[T] with Collection[U]
    // it is ok to look for common super type of T and U but for Comparable[T] and Integer it won't be ok.
    // Maybe we should do this common super type checking only for well known cases?
    val commonSuperTypesForGenericParams = if (superTypeParams.size == subTypeParams.size) {
      // For generic params, in case of not matching classes, it is better to return Unknown than Typed.empty,
      // but we still want to return the precise type - e.g. available fields in records
      superTypeParams.zip(subTypeParams).map { case (l, p) =>
        CommonSupertypeFinder.Default.commonSupertype(l, p)
      }
    } else {
      superTypeParams
    }
    Typed.genericTypeClass(superType, commonSuperTypesForGenericParams)
  }

  private def commonSuperTypeForClassesNotInSameInheritanceLine(
      left: Class[_],
      right: Class[_]
  ): Option[TypingResult] = {
    // None is possible here
    val foundTypes = ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right).map(Typed(_))
    val result     = NonEmptyList.fromList(foundTypes.toList).map(Typed(_))
    classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.Intersection         => result
      case SupertypeClassResolutionStrategy.FallbackToObjectType => result.orElse(Some(Unknown))
    }
  }

}

object CommonSupertypeFinder {

  // This finder has fallback to object type (SingleTypingResult.objType). When no matching super type class found,
  // it returns Any (which currently is expressed as Unknown). It is useful in most cases when you want to determine
  // the supertype of objects
  object Default {
    private val delegate = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.FallbackToObjectType)

    def commonSupertype(left: TypingResult, right: TypingResult): TypingResult = {
      delegate
        .commonSupertypeOpt(left, right)(NumberTypesPromotionStrategy.ToSupertype)
        .getOrElse(
          // We don't return Unknown as a sanity check, that our fallback strategy works correctly and supertypes for object types are used
          throw new IllegalStateException(s"Common super type not found: for $left and $right")
        )
    }

  }

  // It is the strategy that is looking for the intersection of common super types. If it doesn't find it, returns Typed.empty
  // It is useful only when you want to check that types matches e.g. during comparison
  val Intersection = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Intersection)

  private sealed trait SupertypeClassResolutionStrategy

  private object SupertypeClassResolutionStrategy {

    case object FallbackToObjectType extends SupertypeClassResolutionStrategy

    case object Intersection extends SupertypeClassResolutionStrategy

  }

}
