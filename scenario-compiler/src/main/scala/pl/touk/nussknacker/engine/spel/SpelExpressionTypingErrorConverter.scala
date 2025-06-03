package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.clazz.MethodDefinition
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.{
  ArgumentTypeError,
  GenericFunctionError,
  SpelExpressionTypingError
}

case class SpelExpressionTypingErrorConverter(method: MethodDefinition, invocationArguments: List[TypingResult]) {

  def convert(error: GenericFunctionTypingError): SpelExpressionTypingError = {
    val givenSignature = Signature(invocationArguments, None)

    error match {
      case GenericFunctionTypingError.ArgumentTypeError =>
        val expectedSignatures =
          method.signatures.map(info => Signature(info.noVarArgs.map(_.refClazz), info.varArg.map(_.refClazz)))
        ArgumentTypeError(method.name, givenSignature, expectedSignatures)
      case e: GenericFunctionTypingError.CustomError =>
        GenericFunctionError(e.message)
    }
  }

}
