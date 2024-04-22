package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.collection.immutable.ListMap

class UsageStatisticsReportsSettingsDeterminerTest
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  test("should generate correct url with encoded params") {
    UsageStatisticsReportsSettingsDeterminer.prepareUrlString(
      ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")
    ) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

}
