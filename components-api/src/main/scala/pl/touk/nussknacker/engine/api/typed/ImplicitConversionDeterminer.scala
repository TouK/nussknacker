package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, _}
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.StrictConversionDeterminer.{
  canBeConvertedTo,
  canNullBeConvertedTo,
  isAssignable,
  isStrictSubclass,
  singleCanBeConvertedTo
}
import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class determine if type can be subclass of other type. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  *
  * This class, like CommonSupertypeFinder is in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  */
object ImplicitConversionDeterminer {

  private val javaMapClass       = classOf[java.util.Map[_, _]]
  private val javaListClass      = classOf[java.util.List[_]]
  private val arrayOfAnyRefClass = classOf[Array[AnyRef]]

  /**
   * This method checks if `givenType` can by subclass of `superclassCandidate`
   * It will return true if `givenType` is equals to `superclassCandidate` or `givenType` "extends" `superclassCandidate`
   */
  def canBeConvertedTo(givenType: TypingResult, superclassCandidate: TypingResult): ValidatedNel[String, Unit] = {
    (givenType, superclassCandidate) match {
      case (_, Unknown)       => ().validNel
      case (Unknown, _)       => ().validNel
      case (TypedNull, other) => canNullBeConvertedTo(other)
      case (_, TypedNull)     => s"No type can be subclass of ${TypedNull.display}".invalidNel
      case (given: SingleTypingResult, superclass: TypedUnion) =>
        canBeConvertedTo(NonEmptyList.one(given), superclass.possibleTypes)
      case (given: TypedUnion, superclass: SingleTypingResult) =>
        canBeConvertedTo(given.possibleTypes, NonEmptyList.one(superclass))
      case (given: SingleTypingResult, superclass: SingleTypingResult) => singleCanBeConvertedTo(given, superclass)
      case (given: TypedUnion, superclass: TypedUnion) =>
        canBeConvertedTo(given.possibleTypes, superclass.possibleTypes)
    }
  }

  def canBeStrictlyConvertedTo(
      givenType: TypingResult,
      superclassCandidate: TypingResult
  ): ValidatedNel[String, Unit] = {
    (givenType, superclassCandidate) match {
      case (_, Unknown)       => ().validNel
      case (Unknown, _)       => ().validNel
      case (TypedNull, other) => canNullBeConvertedTo(other)
      case (_, TypedNull)     => s"No type can be subclass of ${TypedNull.display}".invalidNel
      case (given: SingleTypingResult, superclass: TypedUnion) =>
        canBeConvertedTo(NonEmptyList.one(given), superclass.possibleTypes)
      case (given: TypedUnion, superclass: SingleTypingResult) =>
        canBeConvertedTo(given.possibleTypes, NonEmptyList.one(superclass))
      case (given: SingleTypingResult, superclass: SingleTypingResult) =>
        singleCanBeStrictlyConvertedTo(given, superclass)
      case (given: TypedUnion, superclass: TypedUnion) =>
        canBeConvertedTo(given.possibleTypes, superclass.possibleTypes)
    }

  }

  def singleCanBeStrictlyConvertedTo(
      givenType: SingleTypingResult,
      superclassCandidate: SingleTypingResult
  ): ValidatedNel[String, Unit] = {
    val givenClass     = givenType.runtimeObjType
    val givenSuperclas = superclassCandidate.runtimeObjType

    isStrictSubclass(givenClass, givenSuperclas)
  }

  def isStrictSubclass(givenClass: TypedClass, givenSuperclass: TypedClass): Validated[NonEmptyList[String], Unit] = {
    condNel(
      givenClass == givenSuperclass,
      (),
      f"${givenClass.display} and ${givenSuperclass.display} are not the same"
    ) orElse
      condNel(
        isAssignable(givenClass.klass, givenSuperclass.klass),
        (),
        s"${givenClass.klass} is not assignable from ${givenSuperclass.klass}"
      )
  }

  def canBeConvertedTo(
      givenTypes: NonEmptyList[SingleTypingResult],
      superclassCandidates: NonEmptyList[SingleTypingResult]
  ): ValidatedNel[String, Unit] = {
    // Would be more safety to do givenTypes.forAll(... superclassCandidates.exists ...) - we wil protect against
    // e.g. (String | Int).canBeSubclassOf(String) which can fail in runtime for Int, but on the other hand we can't block user's intended action.
    // He/she could be sure that in this type, only String will appear. He/she also can't easily downcast (String | Int) to String so leaving here
    // "double exists" looks like a good tradeoff
    condNel(
      givenTypes.exists(given => superclassCandidates.exists(singleCanBeConvertedTo(given, _).isValid)),
      (),
      s"""None of the following types:
         |${givenTypes.map(" - " + _.display).toList.mkString(",\n")}
         |can be a subclass of any of:
         |${superclassCandidates.map(" - " + _.display).toList.mkString(",\n")}""".stripMargin
    )
  }

  private def canNullBeConvertedTo(result: TypingResult): ValidatedNel[String, Unit] = result match {
    // TODO: Null should not be subclass of typed map that has all values assigned.
    case TypedObjectWithValue(_, _) => s"${TypedNull.display} cannot be subclass of type with value".invalidNel
    case _                          => ().validNel
  }

  def singleCanBeConvertedTo(
      givenType: SingleTypingResult,
      superclassCandidate: SingleTypingResult
  ): ValidatedNel[String, Unit] = {
    val objTypeRestriction = classCanBeConvertedTo(givenType, superclassCandidate)
    val typedObjectRestrictions = (_: Unit) =>
      superclassCandidate match {
        case superclass: TypedObjectTypingResult =>
          val givenTypeFields = givenType match {
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
                    canBeConvertedTo(givenFieldType, typ).isValid,
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
      (givenType, superclassCandidate) match {
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
      (givenType, superclassCandidate) match {
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
      (givenType, superclassCandidate) match {
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

  def isStrictSubclass(givenClass: TypedClass, givenSuperclass: TypedClass): Validated[NonEmptyList[String], Unit] = {
    condNel(
      givenClass == givenSuperclass,
      (),
      f"${givenClass.display} and ${givenSuperclass.display} are not the same"
    ) orElse
      condNel(
        isAssignable(givenClass.klass, givenSuperclass.klass),
        (),
        s"${givenClass.klass} is not assignable from ${givenSuperclass.klass}"
      )
  }

  private def classCanBeConvertedTo(
      givenType: SingleTypingResult,
      superclassCandidate: SingleTypingResult
  ): ValidatedNel[String, Unit] = {
    val givenClass      = givenType.runtimeObjType
    val givenSuperclass = superclassCandidate.runtimeObjType

    val equalClassesOrCanAssign = isStrictSubclass(givenClass, givenSuperclass)
    val canBeSubclass = equalClassesOrCanAssign andThen (_ => typeParametersMatches(givenClass, givenSuperclass))
    canBeSubclass orElse canBeConvertedTo(givenType, givenSuperclass)
  }

  // TODO: Conversions should be checked during typing, not during generic usage of TypingResult.canBeSubclassOf(...)
  def canBeConvertedTo(
      givenType: SingleTypingResult,
      superclassCandidate: TypedClass
  ): ValidatedNel[String, Unit] = {
    val errMsgPrefix = s"${givenType.runtimeObjType.display} cannot be converted to ${superclassCandidate.display}"
    condNel(TypeConversionHandler.canBeConvertedTo(givenType, superclassCandidate), (), errMsgPrefix)
  }

  private def typeParametersMatches(givenClass: TypedClass, superclassCandidate: TypedClass) = {
    def canBeSubOrSuperclass(givenClassParam: TypingResult, superclassParam: TypingResult) =
      condNel(
        canBeConvertedTo(givenClassParam, superclassParam).isValid ||
          canBeConvertedTo(superclassParam, givenClassParam).isValid,
        (),
        f"None of ${givenClassParam.display} and ${superclassParam.display} is a subclass of another"
      )

    (givenClass, superclassCandidate) match {
      case (TypedClass(_, givenElementParam :: Nil), TypedClass(superclass, superclassParam :: Nil))
          // Array are invariant but we have built-in conversion between array types - this check should be moved outside this class when we move away canBeConvertedTo as well
          if javaListClass.isAssignableFrom(superclass) || arrayOfAnyRefClass.isAssignableFrom(superclass) =>
        canBeConvertedTo(givenElementParam, superclassParam)
      case (
            TypedClass(_, givenKeyParam :: givenValueParam :: Nil),
            TypedClass(superclass, superclassKeyParam :: superclassValueParam :: Nil)
          ) if javaMapClass.isAssignableFrom(superclass) =>
        // Map's key generic param is invariant. We can't just check givenKeyParam == superclassKeyParam because of Unknown type which is a kind of wildcard
        condNel(
          canBeConvertedTo(givenKeyParam, superclassKeyParam).isValid &&
            canBeConvertedTo(superclassKeyParam, givenKeyParam).isValid,
          (),
          s"Key types of Maps ${givenKeyParam.display} and ${superclassKeyParam.display} are not equals"
        ) andThen (_ => canBeConvertedTo(givenValueParam, superclassValueParam))
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

  // we use explicit autoboxing = true flag, as ClassUtils in commons-lang3:3.3 (used in Flink) cannot handle JDK 11...
  def isAssignable(from: Class[_], to: Class[_]): Boolean =
    ClassUtils.isAssignable(from, to, true)

}
