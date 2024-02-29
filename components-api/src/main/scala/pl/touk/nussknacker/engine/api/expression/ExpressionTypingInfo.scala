package pl.touk.nussknacker.engine.api.expression

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
  * It contains information about intermediate result of typing of expression. Can be used for further processing of expression
  * like some substitutions base on type...
  */
trait ExpressionTypingInfo {

  def typingResult: TypingResult

}
