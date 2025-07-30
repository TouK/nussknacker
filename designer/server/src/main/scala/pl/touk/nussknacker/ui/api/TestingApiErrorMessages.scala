package pl.touk.nussknacker.ui.api

import io.circe.{DecodingFailure, Json}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecordsSerDe.DeserializationError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError

object TestingApiErrorMessages {

  def from(performTestError: ScenarioTestService.PerformTestError): String = {
    performTestError match {
      case PerformTestError.DeserializationError(cause) =>
        cause match {
          case DeserializationError.TooManyCharacters(length, limit) =>
            TestingApiErrorMessages.passedTestData.tooManyCharacters(length, limit)
          case DeserializationError.TooManyRecords(size, limit) =>
            TestingApiErrorMessages.passedTestData.tooManyRecords(size, limit)
          case DeserializationError.NoRecords =>
            TestingApiErrorMessages.passedTestData.empty
          case DeserializationError.RecordParsingError(serializedTestRecord, recordIndex) =>
            TestingApiErrorMessages.problemInSample(recordIndex).parsingError(serializedTestRecord)
          case DeserializationError.RecordsParsingError(message) =>
            TestingApiErrorMessages.passedTestData.parsingError(message)
        }
      case PerformTestError.UnexpectedVariableInTestRecordError(variableName, sourceId, testRecordIndex) =>
        TestingApiErrorMessages
          .problemInSample(testRecordIndex)
          .unexpectedVariableInTestRecordError(variableName, sourceId)
      case PerformTestError.TestRecordVariableDecodingError(
            variableName,
            variableType,
            encodedVariable,
            cause,
            sourceId,
            testRecordIndex
          ) =>
        TestingApiErrorMessages
          .problemInSample(testRecordIndex)
          .testRecordVariableDecodingError(
            variableName,
            variableType,
            encodedVariable,
            cause,
            sourceId
          )
      case PerformTestError.MissingSourceError(sourceId, recordIndex) =>
        TestingApiErrorMessages.problemInSample(recordIndex).missingSource(sourceId.id)
      case PerformTestError.TestResultsSizeExceededError(approxSizeInBytes, maxBytes) =>
        TestingApiErrorMessages.testResultsSizeExceeded(approxSizeInBytes, maxBytes)
    }
  }

  object liveDataFetching {
    def requestedTooManyRecordsToFetch(maxRecordsCount: Int) =
      s"Too many records requested. The maximum number of records permitted is $maxRecordsCount. Contact the system administrator to increase this limit."

    val noLiveDataAvailable =
      "No live test data available. Please ensure that the storage used by source contains at least one data sample"

    val noSourcesWithLiveDataFetching = "No sources with live data fetching support available"

    def tooManyCharacters(length: Int, limit: Int) =
      s"Too many characters were found in the fetched data ($length). The maximum numbers of permitted characters is $limit. Contact the system administrator to increase this limit."
  }

  object passedTestData {
    val empty = "Test data is empty"

    def parsingError(message: String) = s"Test data are in invalid format: $message"

    def tooManyCharacters(length: Int, limit: Int) =
      s"Test data has too many characters ($length). The maximum numbers of permitted characters is $limit. Contact the system administrator to increase this limit."

    def tooManyRecords(count: Int, limit: Int) =
      s"Test data has too many records ($count). The maximum number of records permitted is $limit. Contact the system administrator to increase this limit."
  }

  object testingWithCustomInput {

    def notSupportedBySource(sourceId: NodeId) =
      s"Testing with custom input is not supported by source '$sourceId'"

  }

  case class problemInSample(private val recordIndex: Int) {

    def parsingError(rawTestRecord: String): String = {
      val trimmedRawTestRecord = rawTestRecord.take(300)
      if (trimmedRawTestRecord.length < rawTestRecord.length) {
        messageForSample(s"could not parse (shows fragment): '$trimmedRawTestRecord'")
      } else {
        messageForSample(s"could not parse: '$rawTestRecord'")
      }
    }

    def missingSource(sourceId: String): String =
      messageForSample(s"source with id '$sourceId' doesn't exist in the scenario")

    def unexpectedVariableInTestRecordError(variableName: String, sourceId: NodeId): String =
      messageForSample(s"Unexpected variable [$variableName] for source [$sourceId]")

    def testRecordVariableDecodingError(
        variableName: String,
        variableType: TypingResult,
        encodedVariable: Json,
        cause: DecodingFailure,
        sourceId: NodeId
    ): String =
      messageForSample(
        s"Variable [name=$variableName, type=${variableType.display}, encoded value=${encodedVariable.noSpaces}] decoding error for source [$sourceId]: ${cause.message}"
      )

    private def messageForSample(message: String) =
      s"Problem in sample ${recordIndex + 1} detected: $message"

  }

  def testResultsSizeExceeded(approxSizeInBytes: Long, maxBytes: Long) =
    s"Test results size exceeded (approximate size is $approxSizeInBytes B). The maximum permitted size is $maxBytes B. Contact the system administrator to increase this limit."

}
