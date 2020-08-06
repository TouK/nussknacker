package pl.touk.nussknacker.engine.api.typed

import cats.data.{ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import cats.implicits._
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

  import CanBeSubclassDeterminerHelper._

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
  def canBeSubclassOf(givenType: TypingResult, superclassCandidate: TypingResult): ValidatedNel[String, Unit] = {
    (givenType, superclassCandidate) match {
      case (_, Unknown) => ().validNel
      case (Unknown, _) => ().validNel
      case (given: SingleTypingResult, superclass: TypedUnion) => canBeSubclassOf(Set(given), superclass.possibleTypes)
      case (given: TypedUnion, superclass: SingleTypingResult) => canBeSubclassOf(given.possibleTypes, Set(superclass))
      case (given: SingleTypingResult, superclass: SingleTypingResult) => singleCanBeSubclassOf(given, superclass)
      case (given: TypedUnion, superclass: TypedUnion) => canBeSubclassOf(given.possibleTypes, superclass.possibleTypes)
    }
  }

  private def canBeSubclassOf(givenTypes: Set[SingleTypingResult], superclassCandidates: Set[SingleTypingResult]): ValidatedNel[String, Unit] = {
    ensureOrInvalid(givenTypes.exists(given => superclassCandidates.exists(singleCanBeSubclassOf(given, _).isValid)))(
      f"None of the following types:\n${givenTypes.map(" - " + _.display).mkString(",\n")}\ncan be a subclass of any of:\n${superclassCandidates.map(" - " + _.display).mkString(",\n")}"
    )
  }

  protected def singleCanBeSubclassOf(givenType: SingleTypingResult, superclassCandidate: SingleTypingResult): ValidatedNel[String, Unit] = {

    val typedObjectRestrictions = (_: Unit) => superclassCandidate match {
      case superclass: TypedObjectTypingResult =>
        val givenTypeFields = givenType match {
          case given: TypedObjectTypingResult => given.fields
          case _ => Map.empty[String, TypingResult]
        }

        joinValidatedNels(superclass.fields.toList.map {
          case (name, typ) => givenTypeFields.get(name) match {
            case None =>
              f"Field '$name' is lacking".invalidNel
            case Some(givenFieldType) =>
              ensureOrInvalid(canBeSubclassOf(givenFieldType, typ).isValid)(
                f"Field '$name' is of the wrong type. Excepted: ${givenFieldType.display}, actual: ${typ.display}"
              )
          }
        })
      case _ =>
        ().validNel
    }
    val dictRestriction = (_: Unit) => {
      val errorPrefix = "Dict restriction not met:"
      (givenType, superclassCandidate) match {
        case (given: TypedDict, superclass: TypedDict) =>
          ensureOrInvalid(given.dictId == superclass.dictId)(
            f"$errorPrefix ${givenType.display} and ${superclassCandidate.display} are dicts with unequal IDs")
        case (_: TypedDict, _) =>
          f"$errorPrefix ${givenType.display} is a Dict but ${superclassCandidate.display} not".invalidNel
        case (_, _: TypedDict) =>
          f"$errorPrefix ${superclassCandidate.display} is a Dict but ${givenType.display} not".invalidNel
        case _ =>
          ().validNel
      }
    }
    val taggedValueRestriction = (_: Unit) => {
      (givenType, superclassCandidate) match {
        case (givenTaggedValue: TypedTaggedValue, superclassTaggedValue: TypedTaggedValue) =>
          ensureOrInvalid(givenTaggedValue.tag == superclassTaggedValue.tag)(
            f"Tagged values have unequal tags: ${givenTaggedValue.tag} and ${superclassTaggedValue.tag}")
        case (_: TypedTaggedValue, _) => ().validNel
        case (_, _: TypedTaggedValue) =>
          f"The type is not a tagged value".invalidNel
        case _ => ().validNel
      }
    }
    klassCanBeSubclassOf(givenType.objType, superclassCandidate.objType) andThen typedObjectRestrictions andThen dictRestriction andThen
      taggedValueRestriction
  }

  protected def klassCanBeSubclassOf(givenClass: TypedClass, superclassCandidate: TypedClass): ValidatedNel[String, Unit] = {

    def canBeSubOrSuperclass(t1: TypingResult, t2: TypingResult) =
      ensureOrInvalid(canBeSubclassOf(t1, t2).isValid || canBeSubclassOf(t2, t1).isValid)(
        f"None of ${t1.display} and ${t2.display} is a subclass of another"
      )

    val hasSameTypeParams = (_: Unit) =>
      //we are lax here - the generic type may be co- or contra-variant - and we don't want to
      //throw validation errors in this case. It's better to accept to much than too little
      ensureOrInvalid(superclassCandidate.params.zip(givenClass.params).forall(t => canBeSubOrSuperclass(t._1, t._2).isValid))(
        f"Wrong type parameters"
      )

    val equalClassesOrCanAssign =
      ensureOrInvalid(givenClass == superclassCandidate)(
        f"${givenClass.display} and ${superclassCandidate.display} are not the same") orElse
        isAssignable(givenClass.klass, superclassCandidate.klass)

    val canBeSubclass = equalClassesOrCanAssign andThen hasSameTypeParams
    canBeSubclass orElse canBeConvertedTo(givenClass, superclassCandidate)
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def canBeConvertedTo(givenClass: TypedClass, superclassCandidate: TypedClass): ValidatedNel[String, Unit] = {
    val errMsgPrefix = f"${givenClass.display} cannot be converted to ${superclassCandidate.display}"

    val boxedGivenClass = ClassUtils.primitiveToWrapper(givenClass.klass)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(superclassCandidate.klass)
    // We can't check precision here so we need to be loose here
    // TODO: Add feature flag: strictNumberPrecisionChecking (default false?) and rename strictTypeChecking to strictClassesTypeChecking

    val can = if (NumberTypesPromotionStrategy.isFloatingNumber(boxedSuperclassCandidate) || boxedSuperclassCandidate == classOf[java.math.BigDecimal]) {
      isAssignable(boxedGivenClass, classOf[Number]).isValid
    } else if (NumberTypesPromotionStrategy.isDecimalNumber(boxedSuperclassCandidate)) {
      ConversionFromClassesForDecimals.exists(isAssignable(boxedGivenClass, _).isValid)
    } else {
      false
    }

    ensureOrInvalid(can)(errMsgPrefix)
  }

  //we use explicit autoboxing = true flag, as ClassUtils in commons-lang3:3.3 (used in Flink) cannot handle JDK 11...
  private def isAssignable(from: Class[_], to: Class[_]): ValidatedNel[String, Unit] =
    ensureOrInvalid(ClassUtils.isAssignable(from, to, true))(f"$to is not assignable from $from")
}

object CanBeSubclassDeterminer extends CanBeSubclassDeterminer

object CanBeSubclassDeterminerHelper {

  def joinValidatedNels(nels: Seq[ValidatedNel[String, Unit]]): ValidatedNel[String, Unit] =
    nels.foldLeft(().validNel[String])(_.combine(_))

  def joinValidatedNelsWithParentMsg(nels: Seq[ValidatedNel[String, Unit]])(parentErrMsg: String): ValidatedNel[String, Unit] = {
    val joinedNel = joinValidatedNels(nels)
    val parentNel = ensureOrInvalid(joinedNel.isValid)(parentErrMsg)
    parentNel.combine(joinedNel) // parent's error added before children's errors
  }

  def ensureOrInvalid(cond: Boolean)(errMsg: String): ValidatedNel[String, Unit] =
    if (!cond)
      errMsg.invalidNel
    else
      ().validNel
}