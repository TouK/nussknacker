package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated.condNel
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.AllNumbers
import pl.touk.nussknacker.engine.api.typed.typing.{
  SingleTypingResult,
  TypedClass,
  TypedNull,
  TypedObjectWithValue,
  TypedUnion,
  TypingResult,
  Unknown
}

trait ConversionDeterminer {

  def singleCanBeConvertedTo(
      result: typing.SingleTypingResult,
      result1: typing.SingleTypingResult
  ): ValidatedNel[String, Unit]

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

  private def canNullBeConvertedTo(result: TypingResult): ValidatedNel[String, Unit] = result match {
    // TODO: Null should not be subclass of typed map that has all values assigned.
    case TypedObjectWithValue(_, _) => s"${TypedNull.display} cannot be subclass of type with value".invalidNel
    case _                          => ().validNel
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

  def isAssignable(from: Class[_], to: Class[_]): Boolean
}
