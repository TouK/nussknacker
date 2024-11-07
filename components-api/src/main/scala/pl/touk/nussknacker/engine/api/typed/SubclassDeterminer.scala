package pl.touk.nussknacker.engine.api.typed

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

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

}
