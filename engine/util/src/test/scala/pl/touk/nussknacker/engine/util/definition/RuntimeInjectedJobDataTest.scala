package pl.touk.nussknacker.engine.util.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._

class RuntimeInjectedJobDataTest extends FunSuite with Matchers {

  import RuntimeInjectedJobDataTest._

  test("accessing jobData on uninitialized instance throws exception") {
    val living = new Living
    assertThrows[UninitializedJobDataException] {
      living.jobData
    }
  }
  test("gets jobData from initialized instance") {
    val living = new Living
    living.open(jobData)
    living.jobData shouldEqual jobData
  }
}

object RuntimeInjectedJobDataTest {
  val jobData = JobData(MetaData("", StreamMetaData()), ProcessVersion.empty)

  class Living extends RuntimeInjectedJobData

}
