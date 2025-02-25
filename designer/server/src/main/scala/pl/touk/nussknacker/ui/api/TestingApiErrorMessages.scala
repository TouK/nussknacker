package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.TestDataPreparationError
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError.TooManyCharactersGenerated

object TestingApiErrorMessages {

  val noSourcesWithTestDataGeneration = "No sources with test data generation available"

  val noDataGenerated = "No data was generated."

  val noInputRecords = "No input records found"

  def requestedTooManySamplesToGenerate(maxSamples: Int) =
    s"Too many samples requested, limit is $maxSamples. Please configure 'testDataSettings.maxSamplesCount' to increase the limit"

  def tooManyCharactersGenerated(length: Int, limit: Int) =
    s"$length characters were generated, limit is $limit. Please configure 'testDataSettings.testDataMaxLength' to increase the limit"

  def tooManyCharactersReceived(length: Int, limit: Int) =
    s"Received $length characters, limit is $limit. Please configure 'testDataSettings.testDataMaxLength' to increase the limit"

  def tooManySamplesReceived(count: Int, maxSamples: Int) =
    s"Received $count samples, limit is: $maxSamples. Please configure 'testDataSettings.maxSamplesCount'"

  def recordParsingError(rawTestRecord: String): String = {
    val trimmedRawTestRecord = rawTestRecord.take(300)
    if (trimmedRawTestRecord.length < rawTestRecord.length) {
      s"Could not parse record (shows fragment): '$trimmedRawTestRecord'"
    } else {
      s"Could not parse record: '$rawTestRecord'"
    }
  }

  def missingSourceForRecord(sourceId: String, recordIndex: Int) =
    s"Record ${recordIndex + 1} - scenario does not have source id: '$sourceId'"

  def multipleSourcesRequiredForRecord(recordIndex: Int) =
    s"Record ${recordIndex + 1} - scenario has multiple sources but got record without source id"

  // TODO ljd: human readable unit?
  def testResultsSizeExceeded(approxSizeInBytes: Long, maxBytes: Long) =
    s"Test results size exceeded, approximate size: $approxSizeInBytes, but limit is: $maxBytes. Please configure 'testDataSettings.resultsMaxBytes' to increase the limit"

}
