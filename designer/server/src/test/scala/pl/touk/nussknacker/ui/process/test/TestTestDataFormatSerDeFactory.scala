package pl.touk.nussknacker.ui.process.test

import pl.touk.nussknacker.ui.api.TestDataFormat
import pl.touk.nussknacker.ui.process.test.testdataformat.{
  CommonDataFormatSerDe,
  SourceSpecificDataFormatSerDe,
  TestDataFormatSerDe
}

object TestTestDataFormatSerDeFactory {

  def create(testDataFormat: TestDataFormat.Value): TestDataFormatSerDe = testDataFormat match {
    case TestDataFormat.SourceSpecific => SourceSpecificDataFormatSerDe
    case TestDataFormat.CommonFormat   => CommonDataFormatSerDe
  }

}
