package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.DeserializationError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError
import pl.touk.nussknacker.ui.process.test.TestInfoProvider.TestDataPreparationError

object TestingApiErrorMessages {

  def from(performTestError: ScenarioTestService.PerformTestError): String = {
    performTestError match {
      case PerformTestError.DeserializationError(cause) =>
        cause match {
          case DeserializationError.TooManyCharacters(length, limit) =>
            TestingApiErrorMessages.passedTestData.tooManyCharacters(length, limit)
          case DeserializationError.TooManySamples(size, limit) =>
            TestingApiErrorMessages.passedTestData.tooManySamples(size, limit)
          case DeserializationError.NoRecords =>
            TestingApiErrorMessages.passedTestData.empty
          case DeserializationError.RecordParsingError(rawTestRecord, recordIndex) =>
            TestingApiErrorMessages.problemInSample(recordIndex).parsingError(rawTestRecord)
        }
      case PerformTestError.TestDataPreparationError(cause) =>
        cause match {
          case TestDataPreparationError.MissingSource(sourceId, recordIndex) =>
            TestingApiErrorMessages.problemInSample(recordIndex).missingSource(sourceId.id)
          case TestDataPreparationError.MultipleSourcesRequired(recordIndex) =>
            TestingApiErrorMessages.problemInSample(recordIndex).multipleSourcesRequired
        }
      case PerformTestError.TestResultsSizeExceeded(approxSizeInBytes, maxBytes) =>
        TestingApiErrorMessages.testResultsSizeExceeded(approxSizeInBytes, maxBytes)
    }
  }

  object fetchedLiveData {
    def requestedTooManySamplesToFetch(maxSamples: Int) =
      s"Too many samples requested. Please configure 'testDataSettings.maxSamplesCount' to increase the limit ($maxSamples)"

    val noLiveDataAvailable =
      "No live test data available. Please ensure that the storage used by source contains at least one data sample"

    val noSourcesWithTestDataGeneration = "No sources with test data generation available"

    def tooManyCharacters(length: Int, limit: Int) =
      s"Too many characters were found in the generated test data ($length). Please try to decrease the number of requested samples or configure 'testDataSettings.testDataMaxLength' to increase the limit ($limit)"
  }

  object passedTestData {
    val empty = "Test data is empty"

    def tooManyCharacters(length: Int, limit: Int) =
      s"Test data has too many characters ($length). Please configure 'testDataSettings.testDataMaxLength' to increase the limit ($limit)"

    def tooManySamples(count: Int, maxSamples: Int) =
      s"Test data has too many samples ($count). Please configure 'testDataSettings.maxSamplesCount' to increase the limit ($maxSamples)"
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

    def multipleSourcesRequired: String =
      messageForSample("scenario has multiple sources, but got sample with unspecified source id")

    private def messageForSample(message: String) =
      s"Problem in sample ${recordIndex + 1} detected: $message"
  }

  def testResultsSizeExceeded(approxSizeInBytes: Long, maxBytes: Long) =
    s"Test results size exceeded (approximate size in bytes: $approxSizeInBytes). Please configure 'testDataSettings.resultsMaxBytes' to increase the limit ($maxBytes)"

}
