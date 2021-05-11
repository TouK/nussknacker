package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, ValueWithContext}

/**
 * This is *experimental* trait that allows for providing more details TypeInformation when ValidationContext is known
 * It *probably* will change, by default generic Flink mechanisms are used
 */
trait TypeInformationDetection extends Serializable {

  def forInterpretationResult(validationContext: ValidationContext, output: Option[TypingResult]): TypeInformation[InterpretationResult]

  def forInterpretationResults(results: Map[String, ValidationContext]): TypeInformation[InterpretationResult]

  def forContext(validationContext: ValidationContext): TypeInformation[Context]

  def forValueWithContext[T](validationContext: ValidationContext, value: TypingResult): TypeInformation[ValueWithContext[T]]

}

/**
  * Trait that allows for providing more details TypeInformation when TypingResult is known.
  */
trait TypeInformationDetectionForTypingResult extends TypeInformationDetection {

  def forType(typingResult: TypingResult): TypeInformation[Any]

}
