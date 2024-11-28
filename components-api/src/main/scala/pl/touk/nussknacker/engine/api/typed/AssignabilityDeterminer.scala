package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, _}
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class determine whether we can assign one type to another type - that is if its the same class, a subclass or can be converted to another type. We provide two modes of conversion -
 * 1. Loose conversion is based on the fact that TypingResults are
 * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult). It is basically how SpEL
 * can convert things. Like CommonSupertypeFinder it's in the spirit of "Be type safe as much as possible, but also provide some helpful
 * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
 * 2. Strict conversion checks whether we can convert to a wider type. Eg only widening numerical types
 * are allowed ( Int -> Long). For other types it should work the same as a loose conversion.
 *
  */
object AssignabilityDeterminer {

  private val javaMapClass       = classOf[java.util.Map[_, _]]
  private val javaListClass      = classOf[java.util.List[_]]
  private val arrayOfAnyRefClass = classOf[Array[AnyRef]]

  /**
   * This method checks if `givenType` can by subclass of `superclassCandidate`
   * It will return true if `givenType` is equals to `superclassCandidate` or `givenType` "extends" `superclassCandidate`
   */
  def isAssignableLoose(from: TypingResult, to: TypingResult): ValidatedNel[String, Unit] =
    isAssignable(from, to, LooseConversionChecker)

  def isAssignableStrict(from: TypingResult, to: TypingResult): ValidatedNel[String, Unit] =
    isAssignable(from, to, StrictConversionChecker)

  private def isAssignable(from: TypingResult, to: TypingResult, conversionChecker: ConversionChecker) = {
    (from, to) match {
      case (_, Unknown)       => ().validNel
      case (Unknown, _)       => ().validNel
      case (TypedNull, other) => isNullAsignableTo(other)
      case (_, TypedNull)     => s"No type can be subclass of ${TypedNull.display}".invalidNel
      case (given: SingleTypingResult, superclass: TypedUnion) =>
        isAnyOfAssignableToAnyOf(NonEmptyList.one(given), superclass.possibleTypes, conversionChecker)
      case (given: TypedUnion, superclass: SingleTypingResult) =>
        isAnyOfAssignableToAnyOf(given.possibleTypes, NonEmptyList.one(superclass), conversionChecker)
      case (given: SingleTypingResult, superclass: SingleTypingResult) =>
        isSingleAssignableToSingle(given, superclass, conversionChecker)
      case (given: TypedUnion, superclass: TypedUnion) =>
        isAnyOfAssignableToAnyOf(given.possibleTypes, superclass.possibleTypes, conversionChecker)
    }
  }

  private def isNullAsignableTo(to: TypingResult): ValidatedNel[String, Unit] = to match {
    // TODO: Null should not be subclass of typed map that has all values assigned.
    case TypedObjectWithValue(_, _) => s"${TypedNull.display} cannot be subclass of type with value".invalidNel
    case _                          => ().validNel
  }

  private def isSingleAssignableToSingle(
      from: SingleTypingResult,
      to: SingleTypingResult,
      conversionChecker: ConversionChecker
  ): ValidatedNel[String, Unit] = {
    val objTypeRestriction = isSingleAssignableToTypedClass(from, to.runtimeObjType, conversionChecker)
    val typedObjectRestrictions = (_: Unit) =>
      to match {
        case superclass: TypedObjectTypingResult =>
          val givenTypeFields = from match {
            case given: TypedObjectTypingResult => given.fields
            case _                              => Map.empty[String, TypingResult]
          }

          superclass.fields.toList
            .map { case (name, typ) =>
              givenTypeFields.get(name) match {
                case None =>
                  s"Field '$name' is lacking".invalidNel
                case Some(givenFieldType) =>
                  condNel(
                    isAssignable(givenFieldType, typ, conversionChecker).isValid,
                    (),
                    s"Field '$name' is of the wrong type. Expected: ${givenFieldType.display}, actual: ${typ.display}"
                  )
              }
            }
            .foldLeft(().validNel[String])(_.combine(_))
        case _ =>
          ().validNel
      }
    val dictRestriction = (_: Unit) => {
      (from, to) match {
        case (given: TypedDict, superclass: TypedDict) =>
          condNel(
            given.dictId == superclass.dictId,
            (),
            "The type and the superclass candidate are Dicts with unequal IDs"
          )
        case (_: TypedDict, _) =>
          "The type is a Dict but the superclass candidate not".invalidNel
        case (_, _: TypedDict) =>
          "The superclass candidate is a Dict but the type not".invalidNel
        case _ =>
          ().validNel
      }
    }
    val taggedValueRestriction = (_: Unit) => {
      (from, to) match {
        case (givenTaggedValue: TypedTaggedValue, superclassTaggedValue: TypedTaggedValue) =>
          condNel(
            givenTaggedValue.tag == superclassTaggedValue.tag,
            (),
            s"Tagged values have unequal tags: ${givenTaggedValue.tag} and ${superclassTaggedValue.tag}"
          )
        case (_: TypedTaggedValue, _) => ().validNel
        case (_, _: TypedTaggedValue) =>
          s"The type is not a tagged value".invalidNel
        case _ => ().validNel
      }
    }
    // Type like Integer can be subclass of Integer{5}, because Integer could
    // possibly have value of 5, that would make it subclass of Integer{5}.
    // This allows us to supply unknown Integer to function that requires
    // Integer{5}.
    val dataValueRestriction = (_: Unit) => {
      (from, to) match {
        case (TypedObjectWithValue(_, givenValue), TypedObjectWithValue(_, candidateValue))
            if givenValue == candidateValue =>
          ().validNel
        case (TypedObjectWithValue(_, givenValue), TypedObjectWithValue(_, candidateValue)) =>
          s"Types with value have different values: $givenValue and $candidateValue".invalidNel
        case _ => ().validNel
      }
    }
    objTypeRestriction andThen
      (typedObjectRestrictions combine dictRestriction combine taggedValueRestriction combine dataValueRestriction)
  }

  private def isSingleAssignableToTypedClass(
      from: SingleTypingResult,
      to: TypedClass,
      conversionChecker: ConversionChecker
  ): ValidatedNel[String, Unit] = {
    def typeParametersMatches(givenClass: TypedClass, superclassCandidate: TypedClass) = {
      def canBeSubOrSuperclass(givenClassParam: TypingResult, superclassParam: TypingResult) =
        condNel(
          isAssignable(givenClassParam, superclassParam, conversionChecker).isValid ||
            isAssignable(superclassParam, givenClassParam, conversionChecker).isValid,
          (),
          f"None of ${givenClassParam.display} and ${superclassParam.display} is a subclass of another"
        )

      (givenClass, superclassCandidate) match {
        case (TypedClass(_, givenElementParam :: Nil), TypedClass(superclass, superclassParam :: Nil))
            // Array are invariant but we have built-in conversion between array types - this check should be moved outside this class when we move away canBeConvertedTo as well
            if javaListClass.isAssignableFrom(superclass) || arrayOfAnyRefClass.isAssignableFrom(superclass) =>
          isAssignable(givenElementParam, superclassParam, conversionChecker)
        case (
              TypedClass(_, givenKeyParam :: givenValueParam :: Nil),
              TypedClass(superclass, superclassKeyParam :: superclassValueParam :: Nil)
            ) if javaMapClass.isAssignableFrom(superclass) =>
          // Map's key generic param is invariant. We can't just check givenKeyParam == superclassKeyParam because of Unknown type which is a kind of wildcard
          condNel(
            isAssignable(givenKeyParam, superclassKeyParam, conversionChecker).isValid &&
              isAssignable(superclassKeyParam, givenKeyParam, conversionChecker).isValid,
            (),
            s"Key types of Maps ${givenKeyParam.display} and ${superclassKeyParam.display} are not equals"
          ) andThen (_ => isAssignable(givenValueParam, superclassValueParam, conversionChecker))
        case _ =>
          // for unknown types we are lax - the generic type may be co- contra- or in-variant - and we don't want to
          // return validation errors in this case. It's better to accept to much than too little
          condNel(
            superclassCandidate.params.zip(givenClass.params).forall { case (superclassParam, givenClassParam) =>
              canBeSubOrSuperclass(givenClassParam, superclassParam).isValid
            },
            (),
            s"Wrong type parameters"
          )
      }
    }
    val givenClass = from.runtimeObjType

    val equalClassesOrCanAssign =
      condNel(
        givenClass == to,
        (),
        f"${givenClass.display} and ${to.display} are not the same"
      ) orElse
        isAssignable(givenClass.klass, to.klass)

    val canBeSubclass = equalClassesOrCanAssign andThen (_ => typeParametersMatches(givenClass, to))
    canBeSubclass orElse conversionChecker.isConvertable(from, to)
  }

  private def isAnyOfAssignableToAnyOf(
      from: NonEmptyList[SingleTypingResult],
      to: NonEmptyList[SingleTypingResult],
      conversionChecker: ConversionChecker
  ): ValidatedNel[String, Unit] = {
    // Would be more safety to do givenTypes.forAll(... superclassCandidates.exists ...) - we wil protect against
    // e.g. (String | Int).isAnyOfAssignableToAnyOf(String) which can fail in runtime for Int, but on the other hand we can't block user's intended action.
    // He/she could be sure that in this type, only String will appear. He/she also can't easily downcast (String | Int) to String so leaving here
    // "double exists" looks like a good tradeoff
    condNel(
      from.exists(given => to.exists(isSingleAssignableToSingle(given, _, conversionChecker).isValid)),
      (),
      s"""None of the following types:
         |${from.map(" - " + _.display).toList.mkString(",\n")}
         |can be a subclass of any of:
         |${to.map(" - " + _.display).toList.mkString(",\n")}""".stripMargin
    )
  }

  // we use explicit autoboxing = true flag, as ClassUtils in commons-lang3:3.3 (used in Flink) cannot handle JDK 11...
  private def isAssignable(from: Class[_], to: Class[_]): ValidatedNel[String, Unit] =
    condNel(ClassUtils.isAssignable(from, to, true), (), s"$to is not assignable from $from")

  // TODO: Conversions should be checked during typing, not during generic usage of TypingResult.canBeSubclassOf(...)
  private sealed trait ConversionChecker {

    def isConvertable(
        from: SingleTypingResult,
        to: TypedClass
    ): ValidatedNel[String, Unit]

  }

  private object StrictConversionChecker extends ConversionChecker {

    override def isConvertable(
        from: SingleTypingResult,
        to: TypedClass
    ): ValidatedNel[String, Unit] = {
      val errMsgPrefix =
        s"${from.runtimeObjType.display} cannot be strictly converted to ${to.display}"
      condNel(TypeConversionHandler.canBeStrictlyConvertedTo(from, to), (), errMsgPrefix)
    }

  }

  private object LooseConversionChecker extends ConversionChecker {

    override def isConvertable(
        from: SingleTypingResult,
        to: TypedClass
    ): ValidatedNel[String, Unit] = {
      val errMsgPrefix = s"${from.runtimeObjType.display} cannot be converted to ${to.display}"
      condNel(TypeConversionHandler.canBeLooselyConvertedTo(from, to), (), errMsgPrefix)
    }

  }

}
