package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, _}
import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class determine if type can be subclass of other type. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  *
  * This class, like CommonSupertypeFinder is in spirit of "Be type safety as much as possible, but also provide some helpful
  * conversion for types not in the same jvm class hierarchy like boxed Integer to boxed Long and so on".
  * WARNING: Evaluation of SpEL expressions fit into this spirit, for other language evaluation engines you need to provide such a compatibility.
  */
object ImplicitConversionDeterminer extends ConversionDeterminer {

  private val javaMapClass       = classOf[java.util.Map[_, _]]
  private val javaListClass      = classOf[java.util.List[_]]
  private val arrayOfAnyRefClass = classOf[Array[AnyRef]]

  protected def singleCanBeConvertedTo(
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

  private def classCanBeConvertedTo(
      givenType: SingleTypingResult,
      superclassCandidate: SingleTypingResult
  ): ValidatedNel[String, Unit] = {
    val givenClass      = givenType.runtimeObjType
    val givenSuperclass = superclassCandidate.runtimeObjType

    val equalClassesOrCanAssign = isStrictSubclass(givenClass, givenSuperclass)
    val canBeSubclass = equalClassesOrCanAssign andThen (_ => typeParametersMatches(givenClass, givenSuperclass))
    canBeSubclass orElse canBeConvertedTo(givenType, superclassCandidate)
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

}
