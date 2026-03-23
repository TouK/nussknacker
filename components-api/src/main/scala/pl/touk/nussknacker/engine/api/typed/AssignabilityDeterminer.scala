package pl.touk.nussknacker.engine.api.typed

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated._
import cats.implicits.{catsSyntaxValidatedId, _}
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.ConversionNecessaryForTypeAssignment.{
  ConvertionIsNeeded,
  NoConvertionIsNeeded
}
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.NoConversion
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._

/**
 * This class determine whether we can assign one type to another type - that is if its the same class, a subclass or can be converted to another type.
 * We provide two conversion strategies:
 * 1. Loose conversion is based on the fact that TypingResults are
 * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult). It is basically how SpEL
 * can convert things. Like CommonSupertypeFinder it's in the spirit of "Be type safe as much as possible, but also provide some helpful
 * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
 * 2. Strict conversion checks whether we can convert to a wider type. Eg only widening numerical types
 * are allowed ( Int -> Long). For other types it should work the same as a loose conversion.
 */
private[engine] object AssignabilityDeterminer
    extends AssignabilityDeterminer(TypingConfigurationProvider.load(Thread.currentThread().getContextClassLoader))

private[engine] class AssignabilityDeterminer(typingConfigurationProvider: TypingConfigurationProvider) {

  def typeAfterPotentialConversion(givenType: TypingResult, targetType: TypingResult)(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ): Validated[NonEmptyList[String], TypingResult] = {
    isAssignable(givenType, targetType).map {
      case NoConvertionIsNeeded                    => givenType
      case ConvertionIsNeeded(typeAfterConversion) => typeAfterConversion
    }
  }

  /**
   * @return Valid if assignment is possible. Inner type (ConversionNecessaryForTypeAssignment) tell us if
   *         assignment is possible only after the conversion. For ConversionStrategy = NoConversion it
   *         will always return NoConvertionIsNeeded, for ConversionStrategy = Strict it
   *         can return ConvertionIsNeeded only with numeric type passed in to (only when conversion is necessary and safe)
   */
  def isAssignable(from: TypingResult, to: TypingResult)(
      implicit conversionStrategy: ConversionStrategy
  ): ValidatedNel[String, ConversionNecessaryForTypeAssignment] = {
    (from, to) match {
      case (_, Unknown(_)) => NoConvertionIsNeeded.validNel
      case (Unknown(_), _) =>
        Validated.condNel(
          typingConfigurationProvider.config.allowUnknownToAnyAssignment,
          NoConvertionIsNeeded,
          s"${from.display} cannot be assigned to ${to.display}"
        )
      case (TypedNull, other) => isNullAssignableTo(other)
      case (_, TypedNull)     => s"${from.display} cannot be assigned to ${TypedNull.display}".invalidNel
      case (given: SingleTypingResult, target: TypedUnion) =>
        isAnyOfAssignableToAnyOf(NonEmptyList.one(given), target.possibleTypes)
      case (given: TypedUnion, targe: SingleTypingResult) =>
        isAnyOfAssignableToAnyOf(given.possibleTypes, NonEmptyList.one(targe))
      case (given: SingleTypingResult, target: SingleTypingResult) =>
        isSingleAssignableToSingle(given, target)
      case (given: TypedUnion, target: TypedUnion) =>
        isAnyOfAssignableToAnyOf(given.possibleTypes, target.possibleTypes)
    }
  }

  private def isNullAssignableTo(to: TypingResult): ValidatedNel[String, ConversionNecessaryForTypeAssignment] =
    to match {
      // TODO: Null should not be subclass of typed map that has all values assigned.
      case TypedObjectWithValue(_, _) => s"${TypedNull.display} cannot be assigned to type with value".invalidNel
      case _                          => NoConvertionIsNeeded.validNel
    }

  private def isSingleAssignableToSingle(from: SingleTypingResult, to: SingleTypingResult)(
      implicit conversionStrategy: ConversionStrategy
  ): ValidatedNel[String, ConversionNecessaryForTypeAssignment] = {
    lazy val recordFieldsRestrictions = to match {
      case target: TypedObjectTypingResult =>
        val givenTypeFields = from match {
          case given: TypedObjectTypingResult => given.fields
          case _                              => Map.empty[String, TypingResult]
        }

        target.fields.toList
          .map { case (name, typ) =>
            givenTypeFields.get(name) match {
              case None =>
                s"Field '$name' is lacking".invalidNel
              case Some(givenFieldType) =>
                condNel(
                  isAssignable(givenFieldType, typ).isValid,
                  (),
                  s"Field '$name' is of the wrong type. Expected: ${givenFieldType.display}, actual: ${typ.display}"
                )
            }
          }
          .foldLeft(().validNel[String])(_.combine(_))
      case _ =>
        ().validNel
    }
    lazy val dictRestriction = {
      (from, to) match {
        case (given: TypedDict, target: TypedDict) =>
          condNel(
            given.dictId == target.dictId,
            (),
            "Given type and target type are Dicts with unequal IDs"
          )
        case (_: TypedDict, _) =>
          "Given type is a Dict but the target type not".invalidNel
        case (_, _: TypedDict) =>
          "The target type is a Dict but given type not".invalidNel
        case _ =>
          ().validNel
      }
    }
    lazy val taggedValueRestriction = {
      (from, to) match {
        case (givenTaggedValue: TypedTaggedValue, targetTaggedValue: TypedTaggedValue) =>
          condNel(
            givenTaggedValue.tag == targetTaggedValue.tag,
            (),
            s"Tagged values have unequal tags: ${givenTaggedValue.tag} and ${targetTaggedValue.tag}"
          )
        case (_: TypedTaggedValue, _) => ().validNel
        case (_, _: TypedTaggedValue) =>
          s"Given type is not a tagged value but target type is".invalidNel
        case _ => ().validNel
      }
    }
    // Type like Integer{5} can be assigned to Integer, because Integer could possibly have value of 5 which was erased
    // This allows us to supply unknown Integer to function that requires Integer{5}.
    lazy val dataValueRestriction = {
      (from, to) match {
        case (TypedObjectWithValue(_, givenValue), TypedObjectWithValue(_, candidateValue))
            if givenValue == candidateValue =>
          ().validNel
        case (TypedObjectWithValue(_, givenValue), TypedObjectWithValue(_, candidateValue)) =>
          s"Types with value have different values: $givenValue and $candidateValue".invalidNel
        case _ => ().validNel
      }
    }
    isSingleTypeMatchesTargetObjType(from, to.runtimeObjType) andThen { conversionIsNecessary =>
      (recordFieldsRestrictions combine dictRestriction combine taggedValueRestriction combine dataValueRestriction)
        .map(_ => conversionIsNecessary)
    }
  }

  private def isSingleTypeMatchesTargetObjType(from: SingleTypingResult, to: TypedClass)(
      implicit conversionStrategy: ConversionStrategy
  ): ValidatedNel[String, ConversionNecessaryForTypeAssignment] = {
    def typeParametersMatches(givenClass: TypedClass, targetCandidate: TypedClass, maybeValue: Option[Any]) = {
      def canBeAssignedToOrAssignedFrom(givenClassParam: TypingResult, targetParam: TypingResult) =
        condNel(
          isAssignable(givenClassParam, targetParam).isValid ||
            isAssignable(targetParam, givenClassParam).isValid,
          (),
          f"None of ${givenClassParam.display} and ${targetParam.display} can be assigned to another"
        )

      def isEmptyListOrArrayLiteral(givenClassParam: TypingResult): Option[NoConvertionIsNeeded.type] = {
        givenClassParam match {
          // after splitting Unknown to Any and Nothing, here should be Nothing
          case Unknown(_) =>
            maybeValue match {
              case Some(list: java.util.List[_]) => Option.when(list.isEmpty)(NoConvertionIsNeeded)
              case Some(array: Array[_])         => Option.when(array.isEmpty)(NoConvertionIsNeeded)
              case None | Some(_)                => None
            }
          case _ => None
        }
      }

      def isEmptyMapLiteral(givenClassParam: TypingResult): Option[NoConvertionIsNeeded.type] = {
        givenClassParam match {
          // after splitting Unknown to Any and Nothing, here should be Nothing
          case Unknown(_) =>
            maybeValue match {
              case Some(map: java.util.Map[_, _]) => Option.when(map.isEmpty)(NoConvertionIsNeeded)
              case None | Some(_)                 => None
            }
          case _ => None
        }
      }

      (givenClass, targetCandidate) match {
        case (TypedClass(_, givenElementParam :: Nil), TypedClass(targetClass, targetParam :: Nil))
            if ListClass.isAssignableFrom(targetClass) || ArrayClass.isAssignableFrom(targetClass) =>
          isAssignable(givenElementParam, targetParam).handleErrorWith { errors =>
            isEmptyListOrArrayLiteral(givenElementParam).toValid(errors)
          }
        case (TypedClass(givenClass, givenElementParam :: Nil), TypedClass(targetClass, targetParam :: Nil))
            if CollectionClass == targetClass && CollectionClass.isAssignableFrom(
              givenClass
            ) || givenClass == targetClass && CollectionClass.isAssignableFrom(targetClass) =>
          isAssignable(givenElementParam, targetParam)
        case (
              TypedClass(_, givenKeyParam :: givenValueParam :: Nil),
              TypedClass(targetClass, targetKeyParam :: targetValueParam :: Nil),
            ) if MapClass.isAssignableFrom(targetClass) =>
          // Map's key generic param is invariant. We can't just check givenKeyParam == targetKeyParam because of Unknown type which is a kind of wildcard
          condNel(
            isAssignable(givenKeyParam, targetKeyParam).isValid && (isAssignable(
              targetKeyParam,
              givenKeyParam
            ).isValid || targetKeyParam.isUnknown),
            (),
            s"Key types of Maps ${givenKeyParam.display} and ${targetKeyParam.display} are not equals"
          ) andThen { _ =>
            isAssignable(givenValueParam, targetValueParam).handleErrorWith { errors =>
              isEmptyMapLiteral(givenValueParam).toValid(errors)
            }
          }
        case _ =>
          // for unknown types we are lax - the generic type may be co- contra- or in-variant - and we don't want to
          // return validation errors in this case. It's better to accept too much than too little
          condNel(
            targetCandidate.params.zip(givenClass.params).forall { case (targetParam, givenClassParam) =>
              canBeAssignedToOrAssignedFrom(givenClassParam, targetParam).isValid
            },
            (),
            s"Generic type parameters (${givenClass.params.map(_.display).mkString(", ")}) don't match " +
              s"expected parameters: ${targetCandidate.params.map(_.display).mkString(", ")}"
          )
      }
    }
    val givenClass = from.runtimeObjType

    val equalClassesOrCanAssign =
      condNel(
        givenClass == to,
        (),
        f"${givenClass.display} cannot be assigned to ${to.display}"
      ) orElse
        isAssignable(givenClass.klass, to.klass)

    val canAssignAndParametersMatches = equalClassesOrCanAssign andThen
      (_ => typeParametersMatches(givenClass, to, from.valueOpt)) map (_ => NoConvertionIsNeeded)
    conversionStrategy match {
      case NoConversion => canAssignAndParametersMatches
      case nonEmptyStrategy: NonEmptyConversionStrategy =>
        val conversionResult = TypeConversionHandler.canBeConverted(from, to)(nonEmptyStrategy)
        (canAssignAndParametersMatches, conversionResult) match {
          case (Invalid(_), Some(convertedType)) => Valid(ConvertionIsNeeded(convertedType))
          case (other, _)                        => other
        }
    }

  }

  private def isAnyOfAssignableToAnyOf(from: NonEmptyList[SingleTypingResult], to: NonEmptyList[SingleTypingResult])(
      implicit conversionStrategy: ConversionStrategy
  ): ValidatedNel[String, ConversionNecessaryForTypeAssignment] = {
    // Would be more safety to do givenTypes.forAll(... targetCandidates.exists ...) - we will protect against
    // e.g. (String | Int).isAnyOfAssignableToAnyOf(String) which can fail in runtime for Int, but on the other hand we can't block user's intended action.
    // He/she could be sure that in this type, only String will appear. He/she also can't easily downcast (String | Int) to String so leaving here
    // "double exists" looks like a good tradeoff

    from
      .flatMap { given =>
        to.map { singleTo =>
          isSingleAssignableToSingle(given, singleTo)
        }
      }
      .collectFirst { case valid @ Valid(_) =>
        valid
      }
      .getOrElse {
        s"""None of the following types:
         |${from.map(" - " + _.display).toList.mkString(",\n")}
         |can be a assigned to any of:
         |${to.map(" - " + _.display).toList.mkString(",\n")}""".stripMargin.invalidNel
      }
  }

  // we use explicit autoboxing = true flag, as ClassUtils in commons-lang3:3.3 (used in Flink) cannot handle JDK 11...
  private def isAssignable(from: Class[_], to: Class[_]): ValidatedNel[String, Unit] =
    condNel(ClassUtils.isAssignable(from, to, true), (), s"$from is not assignable to $to")

}

private[engine] sealed trait ConversionNecessaryForTypeAssignment

private[engine] object ConversionNecessaryForTypeAssignment {

  case object NoConvertionIsNeeded extends ConversionNecessaryForTypeAssignment

  case class ConvertionIsNeeded(typeAfterConversion: TypingResult) extends ConversionNecessaryForTypeAssignment

}
