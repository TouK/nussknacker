package pl.touk.nussknacker.engine.process.util

import java.time._

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}

class MetaDataExtractorTest extends FunSuite with Matchers {

  val metaData = MetaData("test", StreamMetaData(), false, Some(ProcessAdditionalFields(None, Set.empty, Map(
    "dateTime" -> "2020-02-25T00:00",
    "date" -> "2020-02-25",
    "time" -> "00:01:00"
  ))))

  test("extract date time property") {
    MetaDataExtractor.extractDateTimeProperty(metaData, "dateTime", LocalDateTime.now()) shouldBe
      LocalDateTime.of(2020, 2, 25, 0, 0)
  }

  test("extract date property") {
    MetaDataExtractor.extractDateProperty(metaData, "date", LocalDate.now()) shouldBe
      LocalDate.of(2020, 2, 25)
  }

  test("extract time property") {
    MetaDataExtractor.extractTimeProperty(metaData, "time", LocalTime.now()) shouldBe
      LocalTime.of(0, 1, 0)
  }
}
