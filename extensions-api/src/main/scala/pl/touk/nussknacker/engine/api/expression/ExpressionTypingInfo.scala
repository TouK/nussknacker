package pl.touk.nussknacker.engine.api.expression

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import sttp.tapir.Schema

/**
  * It contains information about intermediate result of typing of expression. Can be used for further processing of expression
  * like some substitutions base on type...
  */
trait ExpressionTypingInfo {

  def typingResult: TypingResult

}

object ExpressionTypingInfo {
  implicit val schema: Schema[ExpressionTypingInfo] = Schema.string // TODO: type me
}
