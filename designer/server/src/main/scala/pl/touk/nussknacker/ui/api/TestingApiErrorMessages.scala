package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
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
        }
      case PerformTestError.MissingSourceError(sourceId, recordIndex) =>
        TestingApiErrorMessages.problemInSample(recordIndex).missingSource(sourceId.id)
      case PerformTestError.TestResultsSizeExceededError(approxSizeInBytes, maxBytes) =>
        TestingApiErrorMessages.testResultsSizeExceeded(approxSizeInBytes, maxBytes)
      case PerformTestError.ScenarioNodeValidationErrors(errors) =>
        TestingApiErrorMessages.scenarioHasValidationErrors(errors)
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

    private def messageForSample(message: String) =
      s"Problem in sample ${recordIndex + 1} detected: $message"
  }

  def testResultsSizeExceeded(approxSizeInBytes: Long, maxBytes: Long) =
    s"Test results size exceeded (approximate size is $approxSizeInBytes B). The maximum permitted size is $maxBytes B. Contact the system administrator to increase this limit."

  private def scenarioHasValidationErrors(errors: List[NodeValidationError]) =
    s"Only scenario without validation errors can be tested. Errors: $errors"

}
