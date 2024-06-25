package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.CannotGenerateStatisticError

class StatisticsApiHttpServiceSpec extends AnyFunSuite with Matchers {

  test("should return error if the URL cannot be constructed") {
    StatisticsApiHttpService.toURL("xd://stats.nussknacker.io/?f=a+b") shouldBe Left(CannotGenerateStatisticError)
  }

}
