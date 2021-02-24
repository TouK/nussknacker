package pl.touk.nussknacker.engine.util.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentVersion

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
  val jobData: JobData = JobData(MetaData("", StreamMetaData()), ProcessVersion.empty, DeploymentVersion.empty)

  class Living extends RuntimeInjectedJobData

}
