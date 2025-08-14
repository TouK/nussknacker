package pl.touk.nussknacker.engine.testmode

import io.circe.{DecodingFailure, HCursor, Json}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder.{
  TestRecordVariableDecodingError,
  UnexpectedVariableInTestRecordError
}

class CommonTestDataFormatVariablesDecoder(sourceOutputValidationContext: ValidationContext, sourceId: NodeId) {

  def decode(
      jsonVariables: Map[String, Json],
      testRecordIndex: Int
  ): Map[String, Any] = {
    jsonVariables.map { case (variableName, jsonVariable) =>
      val variableType = sourceOutputValidationContext
        .get(variableName)
        .getOrElse(throw UnexpectedVariableInTestRecordError(variableName, sourceId, testRecordIndex))
      val decodedVariable = FromJsonTypingResultBasedDecoder
        .decodeValue(variableType, HCursor.fromJson(jsonVariable))
        .fold(
          err =>
            throw TestRecordVariableDecodingError(
              variableName,
              variableType,
              jsonVariable,
              err,
              sourceId,
              testRecordIndex
            ),
          identity
        )
      variableName -> decodedVariable
    }
  }

}

object CommonTestDataFormatVariablesDecoder {

  sealed abstract class TestRecordVariablesDecodingError(message: String, cause: Exception)
      extends Exception(message, cause) {
    def this(message: String) = this(message, null)
  }

  final case class UnexpectedVariableInTestRecordError(variableName: String, sourceId: NodeId, testRecordIndex: Int)
      extends TestRecordVariablesDecodingError(
        s"Problem in sample ${testRecordIndex + 1} detected: Unexpected variable [$variableName] for source [$sourceId]"
      )

  final case class TestRecordVariableDecodingError(
      variableName: String,
      variableType: TypingResult,
      encodedVariable: Json,
      cause: DecodingFailure,
      sourceId: NodeId,
      testRecordIndex: Int
  ) extends TestRecordVariablesDecodingError(
        s"Problem in sample ${testRecordIndex + 1} detected: Variable [name=$variableName, type=${variableType.display}, encoded value=${encodedVariable.noSpaces}] decoding error for source [$sourceId]: ${cause.message}",
        cause
      )

}
