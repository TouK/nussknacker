package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, _}
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

  protected def singleCanBeSubclassOf(givenType: SingleTypingResult, superclassCandidate: SingleTypingResult): ValidatedNel[String, Unit] = {

    val typedObjectRestrictions = (_: Unit) => superclassCandidate match {
      case superclass: TypedObjectTypingResult =>
        val givenTypeFields = givenType match {
          case given: TypedObjectTypingResult => given.fields
          case _ => Map.empty[String, TypingResult]
        }

        (superclass.fields.toList.map {
          case (name, typ) => givenTypeFields.get(name) match {
            case None =>
              s"Field '$name' is lacking".invalidNel
            case Some(givenFieldType) =>
              condNel(canBeSubclassOf(givenFieldType, typ).isValid, (),
                s"Field '$name' is of the wrong type. Expected: ${givenFieldType.display}, actual: ${typ.display}"
              )
          }
        }).foldLeft(().validNel[String])(_.combine(_))
      case _ =>
        ().validNel
    }
    val dictRestriction = (_: Unit) => {
      (givenType, superclassCandidate) match {
        case (given: TypedDict, superclass: TypedDict) =>
          condNel(given.dictId == superclass.dictId, (),
            "The type and the superclass candidate are Dicts with unequal IDs")
        case (_: TypedDict, _) =>
          "The type is a Dict but the superclass candidate not".invalidNel
        case (_, _: TypedDict) =>
          "The superclass candidate is a Dict but the type not".invalidNel
        case _ =>
          ().validNel
      }
    }
    val taggedValueRestriction = (_: Unit) => {
      (givenType, superclassCandidate) match {
        case (givenTaggedValue: TypedTaggedValue, superclassTaggedValue: TypedTaggedValue) =>
          condNel(givenTaggedValue.tag == superclassTaggedValue.tag, (),
            s"Tagged values have unequal tags: ${givenTaggedValue.tag} and ${superclassTaggedValue.tag}")
        case (_: TypedTaggedValue, _) => ().validNel
        case (_, _: TypedTaggedValue) =>
          s"The type is not a tagged value".invalidNel
        case _ => ().validNel
      }
    }
    classCanBeSubclassOf(givenType.objType, superclassCandidate.objType) andThen
      (typedObjectRestrictions combine dictRestriction combine taggedValueRestriction)
  }

  protected def classCanBeSubclassOf(givenClass: TypedClass, superclassCandidate: TypedClass): ValidatedNel[String, Unit] = {

    def canBeSubOrSuperclass(t1: TypingResult, t2: TypingResult) =
      condNel(canBeSubclassOf(t1, t2).isValid || canBeSubclassOf(t2, t1).isValid, (),
        f"None of ${t1.display} and ${t2.display} is a subclass of another")

    val hasSameTypeParams = (_: Unit) =>
      //we are lax here - the generic type may be co- or contra-variant - and we don't want to
      //throw validation errors in this case. It's better to accept to much than too little
      condNel(superclassCandidate.params.zip(givenClass.params).forall(t => canBeSubOrSuperclass(t._1, t._2).isValid), (),
        s"Wrong type parameters")

    val equalClassesOrCanAssign =
      condNel(givenClass == superclassCandidate, (), f"${givenClass.display} and ${superclassCandidate.display} are not the same") orElse
        isAssignable(givenClass.klass, superclassCandidate.klass)

    val canBeSubclass = equalClassesOrCanAssign andThen hasSameTypeParams
    canBeSubclass orElse canBeConvertedTo(givenClass, superclassCandidate)
  }

  private def canBeSubclassOf(givenTypes: Set[SingleTypingResult], superclassCandidates: Set[SingleTypingResult]): ValidatedNel[String, Unit] = {
    condNel(givenTypes.exists(given => superclassCandidates.exists(singleCanBeSubclassOf(given, _).isValid)), (),
      s"""None of the following types:
         |${givenTypes.map(" - " + _.display).mkString(",\n")}
         |can be a subclass of any of:
         |${superclassCandidates.map(" - " + _.display).mkString(",\n")}""".stripMargin
    )
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def canBeConvertedTo(givenClass: TypedClass, superclassCandidate: TypedClass): ValidatedNel[String, Unit] = {
    val errMsgPrefix = s"${givenClass.display} cannot be converted to ${superclassCandidate.display}"

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

    condNel(can, (), errMsgPrefix)
  }

  //we use explicit autoboxing = true flag, as ClassUtils in commons-lang3:3.3 (used in Flink) cannot handle JDK 11...
  private def isAssignable(from: Class[_], to: Class[_]): ValidatedNel[String, Unit] =
    condNel(ClassUtils.isAssignable(from, to, true), (), s"$to is not assignable from $from")
}

object CanBeSubclassDeterminer extends CanBeSubclassDeterminer