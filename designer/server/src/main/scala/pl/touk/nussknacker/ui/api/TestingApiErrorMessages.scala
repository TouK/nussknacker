package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError.TooManyCharactersGenerated

object TestingApiErrorMessages {

  val noSourcesWithTestDataGeneration = "No sources with test data generation available"

  val noDataGenerated = "No data was generated."

  def tooManySamplesRequested(maxSamples: Int) =
    s"Too many samples requested, limit is $maxSamples. Please configure 'testDataSettings.maxSampleCount' to increase the limit"

  def tooManyCharactersGenerated(length: Int, limit: Int) =
    s"$length characters were generated, limit is $limit. Please configure 'testDataSettings.testDataMaxLength' to increase the limit"
}
