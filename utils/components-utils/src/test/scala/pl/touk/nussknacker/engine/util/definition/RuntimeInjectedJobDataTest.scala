package pl.touk.nussknacker.engine.util.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.util.runtimecontext.TestEngineRuntimeContext

class RuntimeInjectedJobDataTest extends AnyFunSuite with Matchers {

  import RuntimeInjectedJobDataTest._

  test("accessing jobData on uninitialized instance throws exception") {
    val living = new Living
    assertThrows[UninitializedJobDataException] {
      living.jobData
    }
  }
  test("gets jobData from initialized instance") {
    val living = new Living
    living.open(TestEngineRuntimeContext(jobData))
    living.jobData shouldEqual jobData
  }
}

object RuntimeInjectedJobDataTest {
  val jobData: JobData = JobData(MetaData("", StreamMetaData()), ProcessVersion.empty)

  class Living extends RuntimeInjectedJobData
}
