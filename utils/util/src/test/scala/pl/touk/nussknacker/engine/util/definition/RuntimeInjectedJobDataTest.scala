package pl.touk.nussknacker.engine.util.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext, IncContextIdGenerator}
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

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
    living.open(SimpleEngineRuntimeContext(jobData))
    living.jobData shouldEqual jobData
  }
}

object RuntimeInjectedJobDataTest {
  val jobData: JobData = JobData(MetaData("", StreamMetaData()), ProcessVersion.empty)

  class Living extends RuntimeInjectedJobData

  private case class SimpleEngineRuntimeContext(jobData: JobData,
                                                metricsProvider: MetricsProviderForScenario = NoOpMetricsProviderForScenario) extends EngineRuntimeContext {
    override def contextIdGenerator(nodeId: String): ContextIdGenerator = IncContextIdGenerator.withProcessIdNodeIdPrefix(jobData, nodeId)
  }

}
