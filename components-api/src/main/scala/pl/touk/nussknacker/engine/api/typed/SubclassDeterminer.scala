package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated.condNel
import cats.data.ValidatedNel
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.AllNumbers
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypingResult}

object SubclassDeterminer extends ConversionDeterminer {

  def singleCanBeConvertedTo(
      givenType: SingleTypingResult,
      superclassCandidate: SingleTypingResult
  ): ValidatedNel[String, Unit] = {
    val givenClass     = givenType.runtimeObjType
    val givenSuperclas = superclassCandidate.runtimeObjType

    isStrictSubclass(givenClass, givenSuperclas)
  }

  def canBeStrictSubclassOf(givenType: TypingResult, superclassCandidate: TypingResult): ValidatedNel[String, Unit] = {
    this.canBeConvertedTo(givenType, superclassCandidate)
  }

  override def isAssignable(from: Class[_], to: Class[_]): Boolean = {
    (from, to) match {
      case (f, t) if ClassUtils.isAssignable(f, t, true) => true
      // Number double check by hand because lang3 can incorrectly throw false when dealing with java types
      case (f, t) if AllNumbers.contains(f) && AllNumbers.contains(t) =>
        AllNumbers.indexOf(f) >= AllNumbers.indexOf(t)
      case _ => false
    }
  }

}
