package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class determine if type can be subclass of other type. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  *
  * This class, like CommonSupertypeFinder is in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  */
trait CanBeSubclassDeterminer {

  /**
    * java.math.BigDecimal is quite often returned as a wrapper for all kind of numbers (floating and without floating point).
    * Given to this we cannot to be sure if conversion is safe or not based on type (without scale knowledge).
    * So we have two options: enforce user to convert to some type without floating point (e.g. BigInteger) or be loose in this point.
    * Be default we will be loose.
    */
    // TODO: Add feature flag: strictBigDecimalChecking (default false?) and rename strictTypeChecking to strictClassesTypeChecking
  private val ConversionFromClassesForDecimals = NumberTypesPromotionStrategy.DecimalNumbers.toSet + classOf[java.math.BigDecimal]

  /**
    * This method checks if `givenType` can by subclass of `superclassCandidate`
    * It will return true if `givenType` is equals to `superclassCandidate` or `givenType` "extends" `superclassCandidate`
    */
  def canBeSubclassOf(givenType: TypingResult, superclassCandidate: TypingResult): Boolean =
    (givenType, superclassCandidate) match {
      case (_, Unknown) => true
      case (Unknown, _) => true
      case (given: SingleTypingResult, superclass: TypedUnion) => canBeSubclassOf(Set(given), superclass.possibleTypes)
      case (given: TypedUnion, superclass: SingleTypingResult) => canBeSubclassOf(given.possibleTypes, Set(superclass))
      case (given: SingleTypingResult, superclass: SingleTypingResult) => singleCanBeSubclassOf(given, superclass)
      case (given: TypedUnion, superclass: TypedUnion) => canBeSubclassOf(given.possibleTypes, superclass.possibleTypes)
    }

  private def canBeSubclassOf(givenTypes: Set[SingleTypingResult], superclassCandidates: Set[SingleTypingResult]): Boolean =
    givenTypes.exists(given => superclassCandidates.exists(singleCanBeSubclassOf(given, _)))

  protected def singleCanBeSubclassOf(givenType: SingleTypingResult, superclassCandidate: SingleTypingResult): Boolean = {
    def typedObjectRestrictions = superclassCandidate match {
      case superclass: TypedObjectTypingResult =>
        val givenTypeFields = givenType match {
          case given: TypedObjectTypingResult => given.fields
          case _ => Map.empty[String, TypingResult]
        }
        superclass.fields.forall {
          case (name, typ) => givenTypeFields.get(name).exists(canBeSubclassOf(_, typ))
        }
      case _ =>
        true
    }
    def dictRestriction: Boolean = (givenType, superclassCandidate) match {
      case (given: TypedDict, superclass: TypedDict) =>
        given.dictId == superclass.dictId
      case (_: TypedDict, _) =>
        false
      case (_, _: TypedDict) =>
        false
      case _ =>
        true
    }
    def taggedValueRestriction: Boolean = (givenType, superclassCandidate) match {
      case (givenTaggedValue: TypedTaggedValue, superclassTaggedValue: TypedTaggedValue) =>
        givenTaggedValue.tag == superclassTaggedValue.tag
      case (_: TypedTaggedValue, _) => true
      case (_, _: TypedTaggedValue) => false
      case _ => true
    }
    klassCanBeSubclassOf(givenType.objType, superclassCandidate.objType) && typedObjectRestrictions && dictRestriction &&
      taggedValueRestriction
 }

  protected def klassCanBeSubclassOf(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    def hasSameTypeParams =
    //we are lax here - the generic type may be co- or contra-variant - and we don't want to
    //throw validation errors in this case. It's better to accept to much than too little
      superclassCandidate.params.zip(givenClass.params).forall(t => canBeSubclassOf(t._1, t._2) || canBeSubclassOf(t._2, t._1))

    val canBeSubclass = givenClass == superclassCandidate || isAssignable(givenClass.klass, superclassCandidate.klass) && hasSameTypeParams
    canBeSubclass || canBeConvertedTo(givenClass, superclassCandidate)
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def canBeConvertedTo(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    val boxedGivenClass = ClassUtils.primitiveToWrapper(givenClass.klass)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(superclassCandidate.klass)
    // We can't check precision here so we need to be loose here
    // TODO: Add feature flag: strictNumberPrecisionChecking (default false?) and rename strictTypeChecking to strictClassesTypeChecking
    if (NumberTypesPromotionStrategy.isFloatingNumber(boxedSuperclassCandidate) || boxedSuperclassCandidate == classOf[java.math.BigDecimal]) {
      isAssignable(boxedGivenClass, classOf[Number])
    } else if (NumberTypesPromotionStrategy.isDecimalNumber(boxedSuperclassCandidate)) {
      ConversionFromClassesForDecimals.exists(isAssignable(boxedGivenClass, _))
    } else {
      false
    }
  }

  //we use explicit autoboxing = true flag, as ClassUtils in commons-lang3:3.3 (used in Flink) cannot handle JDK 11...
  private def isAssignable(from: Class[_], to: Class[_]) = ClassUtils.isAssignable(from, to, true)
}

object CanBeSubclassDeterminer extends CanBeSubclassDeterminer