package pl.touk.nussknacker.engine.api.typed.supertype

import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder.{
  SupertypeClassResolutionStrategy,
  looseFinder
}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.Implicits.RichIterable

import scala.collection.immutable.ListMap

/**
  * This class finding common supertype of two types. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  */
class CommonSupertypeFinder private (classResolutionStrategy: SupertypeClassResolutionStrategy) {

  // It returns None if none supertype found (it is possible only for SupertypeClassResolutionStrategy.Intersection)
  // see commonSuperTypeForClassesNotInSameInheritanceLine and fallback in singleCommonSupertype
  def commonSupertypeOpt(left: TypingResult, right: TypingResult): Option[TypingResult] = {
    (left, right) match {
      case (Unknown, _)   => Some(Unknown) // can't be sure intention of user - union is more secure than intersection
      case (_, Unknown)   => Some(Unknown)
      case (TypedNull, r) => Some(commonSupertypeWithNull(r))
      case (r, TypedNull) => Some(commonSupertypeWithNull(r))
      case (l: SingleTypingResult, r: TypedUnion) =>
        commonSupertype(NonEmptyList.one(l), r.possibleTypes).map(Typed(_))
      case (l: TypedUnion, r: SingleTypingResult) =>
        commonSupertype(l.possibleTypes, NonEmptyList.one(r)).map(Typed(_))
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
  // It can generate a lot of combinations for unions, some of them will be reduced inside Typed.apply(nel)
  // TODO: Do this smarter - a little step towards this would be smarter folding of types inside Typed.apply(nel) - see comment there
  //       Another thing that we can consider is not computing supertype at all (use it only for equals) and always return Union for LooseWithFallbackToObjectType
  //       This approach will generate a long types e.g. for union of records and we treat Unions loose in typing - see CanBeSubclassDeterminer.canBeSubclassOf
  //       but maybe it is not so bad
  private def commonSupertype(
      left: NonEmptyList[SingleTypingResult],
      right: NonEmptyList[SingleTypingResult]
  ): Option[NonEmptyList[TypingResult]] = {
    // .sequence won't do the work because it returns None if any element of list returned None
    val combinations = left.flatMap(l => right.map(singleCommonSupertype(l, _))).toList.flatten
    NonEmptyList.fromList(combinations)
  }

  private def singleCommonSupertype(left: SingleTypingResult, right: SingleTypingResult): Option[TypingResult] = {
    def fallback = classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.LooseWithFallbackToObjectType =>
        classCommonSupertype(left.runtimeObjType, right.runtimeObjType)
      case SupertypeClassResolutionStrategy.Intersection => None
    }
    (left, right) match {
      case (l, r) if l == r => Some(l)
      // TODO We can't do at the beginning if (l.canBeSubclassOf(r) => Some(l) and the same in opposite direction
      //      because canBeSubclassOf handles conversions and many more - see comment next to it
      case (l: TypedClass, r: TypedClass)                           => classCommonSupertype(l, r)
      case (l: TypedObjectTypingResult, r: TypedObjectTypingResult) =>
        // In most cases we compare java.util.Map or GenericRecord, the only difference can be on generic params, but
        // still we'll got a class here, so this getOrElse should occur in the rare situations
        looseFinder
          .classCommonSupertypeReturningTypedClass(l.runtimeObjType, r.runtimeObjType)
          .map { commonSupertype =>
            val fields = prepareFields(l, r)
            // We don't return None in case when fields are empty, because we can't be sure the intention of the user
            // e.g. someone can pass json object with missing field declared as optional in schema
            // and want to compare it with literal record that doesn't have this field
            Typed.record(fields, commonSupertype)
          }
          .orElse(fallback)
      case (l: TypedObjectTypingResult, r) => singleCommonSupertype(l.runtimeObjType, r)
      case (l, r: TypedObjectTypingResult) => singleCommonSupertype(l, r.runtimeObjType)
      case (TypedTaggedValue(leftType, leftTag), TypedTaggedValue(rightType, rightTag)) if leftTag == rightTag =>
        singleCommonSupertype(leftType, rightType).map {
          case single: SingleTypingResult => TypedTaggedValue(single, leftTag)
          case other                      => other
        }
      case (_: TypedTaggedValue, _) => fallback
      case (_, _: TypedTaggedValue) => fallback
      case (TypedObjectWithValue(leftType, leftValue), TypedObjectWithValue(rightType, rightValue))
          if leftValue == rightValue =>
        classCommonSupertype(leftType, rightType).map {
          case typedClass: TypedClass => TypedObjectWithValue(typedClass, leftValue)
          case other                  => other
        }
      case (l: TypedObjectWithValue, r) => singleCommonSupertype(l.underlying, r)
      case (l, r: TypedObjectWithValue) => singleCommonSupertype(l, r.underlying)
      case (_: TypedDict, _)            => fallback
      case (_, _: TypedDict)            => fallback
    }
  }

  private def prepareFields(l: TypedObjectTypingResult, r: TypedObjectTypingResult): Iterable[(String, TypingResult)] =
    (l.fields.toList ++ r.fields.toList)
      .orderedGroupBy(_._1)
      .map { case (key, value) => key -> value.map(_._2) }
      .flatMap {
        case (fieldName, singleType :: Nil) =>
          classResolutionStrategy match {
            case SupertypeClassResolutionStrategy.Intersection                  => None
            case SupertypeClassResolutionStrategy.LooseWithFallbackToObjectType => Some(fieldName -> singleType)
          }
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
  private def classCommonSupertypeReturningTypedClass(left: TypedClass, right: TypedClass): Option[TypedClass] = {
    classCommonSupertype(left, right).flatMap {
      case tc: TypedClass => Some(tc)
      case _              => None // empty, union and so on
    }
  }

  private def classCommonSupertype(left: TypedClass, right: TypedClass): Option[TypingResult] = {
    // TypedClass.klass are already boxed
    if (left.klass.isAssignableFrom(right.klass)) {
      genericClassWithSuperTypeParams(left.klass, left.params, right.params)
    } else if (right.klass.isAssignableFrom(left.klass)) {
      genericClassWithSuperTypeParams(right.klass, right.params, left.params)
    } else {
      // until here things are rather simple
      commonSuperTypeForClassesNotInSameInheritanceLine(left.klass, right.klass)
    }
  }

  private def genericClassWithSuperTypeParams(
      superType: Class[_],
      superTypeParams: List[TypingResult],
      subTypeParams: List[TypingResult]
  ): Option[TypedClass] = {
    // Here is a little bit heuristics. We are not sure what generic types we are comparing, for List[T] with Collection[U]
    // it is ok to look for common super type of T and U but for Comparable[T] and Integer it won't be ok.
    // Maybe we should do this common super type checking only for well known cases?
    val commonSuperTypesForGenericParamsOpt = if (superTypeParams.size == subTypeParams.size) {
      classResolutionStrategy match {
        case SupertypeClassResolutionStrategy.Intersection =>
          superTypeParams
            .zip(subTypeParams)
            .map { case (l, p) =>
              commonSupertypeOpt(l, p)
            }
            .sequence
        case SupertypeClassResolutionStrategy.LooseWithFallbackToObjectType =>
          Some(
            superTypeParams.zip(subTypeParams).map { case (l, p) =>
              commonSupertypeOpt(l, p).getOrElse(Unknown)
            }
          )
      }
    } else {
      Some(superTypeParams)
    }
    commonSuperTypesForGenericParamsOpt.map(Typed.genericTypeClass(superType, _))
  }

  private def commonSuperTypeForClassesNotInSameInheritanceLine(
      left: Class[_],
      right: Class[_]
  ): Option[TypingResult] = {
    val foundTypes = ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right).map(Typed(_))
    // None is possible here
    val result = NonEmptyList.fromList(foundTypes.toList).map(Typed(_))
    classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.Intersection                  => result
      case SupertypeClassResolutionStrategy.LooseWithFallbackToObjectType => result.orElse(Some(Unknown))
    }
  }

}

object CommonSupertypeFinder {

  private val looseFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.LooseWithFallbackToObjectType)

  // This finder has fallback to object type (SingleTypingResult.objType). When no matching super type class found,
  // it returns Any (which currently is expressed as Unknown). It is useful in most cases when you want to determine
  // the supertype of objects
  object Default {

    def commonSupertype(left: TypingResult, right: TypingResult): TypingResult = {
      looseFinder
        .commonSupertypeOpt(left, right)
        .getOrElse(
          // We don't return Unknown as a sanity check, that our fallback strategy works correctly and supertypes for object types are used
          throw new IllegalStateException(s"Common super type not found: for $left and $right")
        )
    }

  }

  // It is the strategy that is looking for the intersection of common super types. If it doesn't find it, returns None
  // It is useful only when you want to check that types matches e.g. during comparison
  val Intersection = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Intersection)

  private sealed trait SupertypeClassResolutionStrategy

  private object SupertypeClassResolutionStrategy {

    case object LooseWithFallbackToObjectType extends SupertypeClassResolutionStrategy

    case object Intersection extends SupertypeClassResolutionStrategy

  }

}
