package pl.touk.nussknacker.engine.api.typed.supertype

import java.lang

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing._

import scala.collection.mutable

/**
  * This class finding common supertype of two types. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  * It has very similar logic to CanBeSubclassDeterminer
  */
class CommonSupertypeFinder(classResolutionStrategy: SupertypeClassResolutionStrategy) {

  def commonSupertype(left: TypingResult, right: TypingResult)
                     (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult =
    (left, right) match {
      case (Unknown, _) => Unknown // can't be sure intention of user - union is more secure than intersection
      case (_, Unknown) => Unknown
      case (f: SingleTypingResult, s: TypedUnion) => Typed(commonSupertype(Set(f), s.possibleTypes))
      case (f: TypedUnion, s: SingleTypingResult) => Typed(commonSupertype(f.possibleTypes, Set(s)))
      case (f: SingleTypingResult, s: SingleTypingResult) => singleCommonSupertype(f, s)
      case (f: TypedUnion, s: TypedUnion) => Typed(commonSupertype(f.possibleTypes, s.possibleTypes))
    }

  private def commonSupertype(leftSet: Set[SingleTypingResult], rightSet: Set[SingleTypingResult])
                             (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): Set[TypingResult] =
    leftSet.flatMap(f => rightSet.map(singleCommonSupertype(f, _)))


  private def singleCommonSupertype(left: SingleTypingResult, right: SingleTypingResult)
                                   (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult =
    (left, right) match {
      case (l: TypedObjectTypingResult, r: TypedObjectTypingResult) =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType).map { commonSupertype =>
            // can't be sure intention of user - union of fields is more secure than intersection
            val fields = unionOfFields(l, r)
            TypedObjectTypingResult(fields, commonSupertype)
          }.getOrElse(Typed.empty)
        }
      case (_: TypedObjectTypingResult, _) => Typed.empty
      case (_, _: TypedObjectTypingResult) => Typed.empty
      case (l@TypedDict(leftDictId, _, leftLabels), r@TypedDict(rightDictId, _, rightLabels)) if leftDictId == rightDictId && leftLabels == rightLabels =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType).map { commonSupertype =>
            TypedDict(leftDictId, commonSupertype, leftLabels)
          }.getOrElse(Typed.empty)
        }
      case (_: TypedDict, _) => Typed.empty
      case (_, _: TypedDict) => Typed.empty
      case (l@TypedTaggedValue(leftType, leftTag), r@TypedTaggedValue(rightType, rightTag)) if leftTag == rightTag =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          Option(singleCommonSupertype(leftType, rightType))
            .collect {
              case single: SingleTypingResult => TypedTaggedValue(single, leftTag)
            }
            .getOrElse(Typed.empty)
        }
      case (_: TypedTaggedValue, _) => Typed.empty
      case (_, _: TypedTaggedValue) => Typed.empty
      case (f: TypedClass, s: TypedClass) => klassCommonSupertype(f, s)
    }
  
  private def checkDirectEqualityOrMorePreciseCommonSupertype[T <: SingleTypingResult](left: T, right: T)(preciseCommonSupertype: => TypingResult) = {
    if (left == right) {
      left
    } else {
      preciseCommonSupertype
    }
  }

  private def unionOfFields(l: TypedObjectTypingResult, r: TypedObjectTypingResult)
                           (implicit numberPromotionStrategy: NumberTypesPromotionStrategy) = {
    (l.fields.toList ++ r.fields.toList).groupBy(_._1).mapValues(_.map(_._2)).flatMap {
      case (fieldName, leftType :: rightType :: Nil) =>
        val common = commonSupertype(leftType, rightType)
        if (common == Typed.empty)
          None // fields type collision - skipping this field
        else
          Some(fieldName -> common)
      case (fieldName, singleType :: Nil) =>
        Some(fieldName -> singleType)
      case (_, longerList) =>
        throw new IllegalArgumentException("Computing union of more than two fields: " + longerList) // shouldn't happen
    }
  }

  // This implementation is because TypedObjectTypingResult has underlying TypedClass instead of TypingResult
  private def klassCommonSupertypeReturningTypedClass(left: TypedClass, right: TypedClass)
                                                     (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): Option[TypedClass] = {
    val boxedLeftClass = boxClass(left.klass)
    val boxedRightClass = boxClass(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(isSimpleType)) {
      commonSuperTypeForSimpleTypes(boxedLeftClass, boxedRightClass) match {
        case tc: TypedClass => Some(tc)
        case TypedUnion(types) if types.nonEmpty && types.forall(_.canBeSubclassOf(Typed[Number])) => Some(TypedClass[Number])
        case _ => None // empty e.g. conflicting simple types
      }
    } else {
      val forComplexTypes = commonSuperTypeForComplexTypes(boxedLeftClass, boxedRightClass)
      forComplexTypes match {
        case tc: TypedClass => Some(tc)
        case _ => None // empty, union and so on
      }
    }
  }

  private def klassCommonSupertype(left: TypedClass, right: TypedClass)
                                  (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult = {
    val boxedLeftClass = boxClass(left.klass)
    val boxedRightClass = boxClass(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(isSimpleType)) {
      commonSuperTypeForSimpleTypes(boxedLeftClass, boxedRightClass)
    } else {
      commonSuperTypeForComplexTypes(boxedLeftClass, boxedRightClass)
    }
  }

  private def commonSuperTypeForSimpleTypes(left: Class[_], right: Class[_])
                                           (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult = {
    if (classOf[Number].isAssignableFrom(left) && classOf[Number].isAssignableFrom(right))
      numberPromotionStrategy.promote(left, right)
    else if (left == right)
      TypedClass(ClazzRef(left))
    else
      Typed.empty
  }

  private def commonSuperTypeForComplexTypes(left: Class[_], right: Class[_]) = {
    if (left.isAssignableFrom(right)) {
      Typed(left)
    } else if (right.isAssignableFrom(left)) {
      Typed(right)
    } else {
      // until here things are rather simple
      Typed(commonSuperTypeForClassesNotInSameInheritanceLine(left, right).map(Typed(_)))
    }
  }

  private def commonSuperTypeForClassesNotInSameInheritanceLine(left: Class[_], right: Class[_]): Set[Class[_]] = {
    classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.Intersection => ClsssHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right)
      case SupertypeClassResolutionStrategy.Union => Set(left, right)
    }
  }

  private def boxClass(clazz: Class[_]) =
    clazz match {
      case p if p == classOf[Boolean] => classOf[lang.Boolean]
      case p if p == classOf[Byte] => classOf[lang.Byte]
      case p if p == classOf[Character] => classOf[Character]
      case p if p == classOf[Short] => classOf[lang.Short]
      case p if p == classOf[Int] => classOf[Integer]
      case p if p == classOf[Long] => classOf[lang.Long]
      case p if p == classOf[Float] => classOf[lang.Float]
      case p if p == classOf[Double] => classOf[lang.Double]
      case _ => clazz
    }

  private def isSimpleType(clazz: Class[_]) =
    clazz == classOf[java.lang.Boolean] || clazz == classOf[String] || classOf[Number].isAssignableFrom(clazz)

}

sealed trait SupertypeClassResolutionStrategy

object SupertypeClassResolutionStrategy {

  case object Intersection extends SupertypeClassResolutionStrategy

  case object Union extends SupertypeClassResolutionStrategy

}