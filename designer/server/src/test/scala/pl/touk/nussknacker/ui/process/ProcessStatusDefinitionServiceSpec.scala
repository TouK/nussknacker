package pl.touk.nussknacker.ui.process

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class ProcessStatusDefinitionServiceSpec extends AnyFunSuite with Matchers with ScalatestRouteTest with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  test("just a draft") {
    val service = new ProcessStatusDefinitionService(typeToConfig, processCategoryService)
    val definitions = service.fetchStateDefinitions()
    println(definitions)
  }

}
