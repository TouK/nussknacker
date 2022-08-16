package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericFunctionTypingError, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.TypeInfos.MethodInfo
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.{ArgumentTypeError, GenericFunctionError}

case class SpelExpressionParseErrorConverter(methodInfo: MethodInfo, invocationArguments: List[TypingResult]) {
  def convert(error: GenericFunctionTypingError): ExpressionParseError = {
    val givenSignature = Signature(invocationArguments, None)

    error match {
      case GenericFunctionTypingError.ArgumentTypeError =>
        val expectedSignatures = methodInfo.signatures.map(info =>
          Signature(info.noVarArgs.map(_.refClazz), info.varArg.map(_.refClazz)))
        ArgumentTypeError(methodInfo.name, givenSignature, expectedSignatures)
      case e: GenericFunctionTypingError.CustomError =>
        GenericFunctionError(e.message)
    }
  }
}
