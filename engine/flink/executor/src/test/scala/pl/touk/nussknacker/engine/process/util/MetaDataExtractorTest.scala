package pl.touk.nussknacker.engine.process.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

import java.time._

class MetaDataExtractorTest extends AnyFunSuite with Matchers {

  private val metaData = MetaData.combineTypeSpecificProperties(
    id = "test",
    typeSpecificData = StreamMetaData(),
    additionalFields = ProcessAdditionalFields(
      None,
      Map(
        "dateTime" -> "2020-02-25T00:00",
        "date"     -> "2020-02-25",
        "time"     -> "00:01:00",
        "duration" -> "P3DT2H",
        "period"   -> "P3Y2M"
      ),
      StreamMetaData.typeName
    )
  )

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

  test("extract duration property") {
    MetaDataExtractor.extractDurationProperty(metaData, "duration", Duration.ZERO) shouldBe Duration.ofHours(74)
  }

  test("extract period property") {
    MetaDataExtractor.extractPeriodProperty(metaData, "period", Period.ZERO) shouldBe Period.of(3, 2, 0)
  }

}
