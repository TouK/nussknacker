package pl.touk.nussknacker.engine.api.typed.supertype

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
class CommonSupertypeFinder(classResolutionStrategy: SupertypeClassResolutionStrategy, strictTaggedTypesChecking: Boolean) {

  def commonSupertype(left: TypingResult, right: TypingResult)
                     (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult =
    (left, right) match {
      case (Unknown, _) => Unknown // can't be sure intention of user - union is more secure than intersection
      case (_, Unknown) => Unknown
      case (l: SingleTypingResult, r: TypedUnion) => Typed(commonSupertype(Set(l), r.possibleTypes))
      case (l: TypedUnion, r: SingleTypingResult) => Typed(commonSupertype(l.possibleTypes, Set(r)))
      case (l: SingleTypingResult, r: SingleTypingResult) => singleCommonSupertype(l, r)
      case (l: TypedUnion, r: TypedUnion) => Typed(commonSupertype(l.possibleTypes, r.possibleTypes))
    }

  private def commonSupertype(leftSet: Set[SingleTypingResult], rightSet: Set[SingleTypingResult])
                             (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): Set[TypingResult] =
    leftSet.flatMap(l => rightSet.map(singleCommonSupertype(l, _)))


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
      case (l: TypedDict, r: TypedDict) if l.dictId == r.dictId =>
        checkDirectEqualityOrMorePreciseCommonSupertype(l, r) {
          klassCommonSupertypeReturningTypedClass(l.objType, r.objType).map { _ =>
            l // should we recognize static vs dynamic and compute some union?
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
      case (TypedTaggedValue(leftType, _), notTaggedRightType) if !strictTaggedTypesChecking =>
        singleCommonSupertype(leftType, notTaggedRightType)
      case (_: TypedTaggedValue, _) => Typed.empty
      case (notTaggedLeftType, TypedTaggedValue(rightType, _)) if !strictTaggedTypesChecking =>
        singleCommonSupertype(notTaggedLeftType, rightType)
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
                           (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): List[(String, TypingResult)] = {
    val leftFields = l.fields.toList
    val rightFields = r.fields.toList
    val leftFieldNames = leftFields.map(_._1)
    val (rightIntersect, rightDoesNotIntersect) = rightFields.partition {
      case (rightFieldName, _) => leftFieldNames.contains(rightFieldName)
    }

    val leftFieldsWithRightCommonFields = leftFields.map { case (name, leftType) =>
      name ->  (leftType :: rightIntersect.filter(name == _._1).map(_._2))
    }.flatMap {
      case (fieldName, leftType :: Nil) =>
        fieldName -> leftType :: Nil
      case (fieldName, leftType :: rightType :: Nil) if leftType == rightType =>
        fieldName -> leftType :: Nil
      case (fieldName, leftType :: rightType :: Nil) =>
        val leastUpperBound = commonSupertype(leftType, rightType)
        if (leastUpperBound == Typed.empty)
          Nil // fields type collision - skipping this field
        else
          (fieldName, leastUpperBound) :: Nil
    }
    leftFieldsWithRightCommonFields ++ rightDoesNotIntersect
}

  // This implementation is because TypedObjectTypingResult has underlying TypedClass instead of TypingResult
  private def klassCommonSupertypeReturningTypedClass(left: TypedClass, right: TypedClass)
                                                     (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): Option[TypedClass] = {
    val boxedLeftClass = ClassUtils.primitiveToWrapper(left.klass)
    val boxedRightClass = ClassUtils.primitiveToWrapper(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(isSimpleType)) {
      commonSuperTypeForSimpleTypes(boxedLeftClass, boxedRightClass) match {
        case tc: TypedClass => Some(tc)
        case TypedUnion(types) if types.nonEmpty && types.forall(_.canBeSubclassOf(Typed[Number])) => Some(Typed.typedClass[Number])
        case _ => None // empty e.g. conflicting simple types
      }
    } else {
      val forComplexTypes = commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params)
      forComplexTypes match {
        case tc: TypedClass => Some(tc)
        case _ => None // empty, union and so on
      }
    }
  }

  private def klassCommonSupertype(left: TypedClass, right: TypedClass)
                                  (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult = {
    val boxedLeftClass = ClassUtils.primitiveToWrapper(left.klass)
    val boxedRightClass = ClassUtils.primitiveToWrapper(right.klass)
    if (List(boxedLeftClass, boxedRightClass).forall(isSimpleType)) {
      commonSuperTypeForSimpleTypes(boxedLeftClass, boxedRightClass)
    } else {
      commonSuperTypeForComplexTypes(boxedLeftClass, left.params, boxedRightClass, right.params)
    }
  }

  private def commonSuperTypeForSimpleTypes(left: Class[_], right: Class[_])
                                           (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingResult = {
    if (classOf[Number].isAssignableFrom(left) && classOf[Number].isAssignableFrom(right))
      numberPromotionStrategy.promoteClasses(left, right)
    else if (left == right)
      Typed(left)
    else
      Typed.empty
  }

  private def commonSuperTypeForComplexTypes(left: Class[_], leftParams: List[TypingResult], right: Class[_], rightParams: List[TypingResult])
                                            (implicit numberPromotionStrategy: NumberTypesPromotionStrategy) = {
    if (left.isAssignableFrom(right)) {
      Typed.genericTypeClass(left, commonSuperTypesForGenericParams(leftParams, rightParams))
    } else if (right.isAssignableFrom(left)) {
      Typed.genericTypeClass(right, commonSuperTypesForGenericParams(leftParams, rightParams))
    } else {
      // until here things are rather simple
      Typed(commonSuperTypeForClassesNotInSameInheritanceLine(left, right).map(Typed(_)))
    }
  }

  private def commonSuperTypesForGenericParams(leftParams: List[TypingResult], rightParams: List[TypingResult])
                                              (implicit numberPromotionStrategy: NumberTypesPromotionStrategy) = {
    leftParams.zip(rightParams).map { case (l, p) => commonSupertype(l, p) }
  }

  private def commonSuperTypeForClassesNotInSameInheritanceLine(left: Class[_], right: Class[_]): Set[Class[_]] = {
    classResolutionStrategy match {
      case SupertypeClassResolutionStrategy.Intersection => ClassHierarchyCommonSupertypeFinder.findCommonSupertypes(left, right)
      case SupertypeClassResolutionStrategy.Union => Set(left, right)
    }
  }

  private def isSimpleType(clazz: Class[_]) =
    clazz == classOf[java.lang.Boolean] || clazz == classOf[String] || classOf[Number].isAssignableFrom(clazz)

}

sealed trait SupertypeClassResolutionStrategy

object SupertypeClassResolutionStrategy {

  case object Intersection extends SupertypeClassResolutionStrategy

  case object Union extends SupertypeClassResolutionStrategy

}
