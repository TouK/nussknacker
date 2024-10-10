package pl.touk.nussknacker.openapi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.http.HttpEnricherComponentProvider
import pl.touk.nussknacker.test.AvailablePortFinder

class HttpEnricherTestSuite extends AnyFunSuite with BeforeAndAfterAll with FlinkSpec with Matchers {

  protected val wireMock: WireMockServer = {
    val server = AvailablePortFinder.withAvailablePortsBlocked(1)(l => {
      new WireMockServer(
        WireMockConfiguration
          .wireMockConfig()
          .port(l.head)
      )
    })
    server.start()
    server
  }

  override protected def afterAll(): Unit = {
    try {
      wireMock.stop()
    } finally {
      super.afterAll()
    }
  }

  protected val noConfigHttpEnricherName = "no-config-http"

  protected lazy val additionalComponents: List[ComponentDefinition] = List.empty

  protected lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(
      new HttpEnricherComponentProvider()
        .create(
          ConfigFactory.empty(),
          ProcessObjectDependencies.withConfig(ConfigFactory.empty())
        )
        .head
        .copy(name = noConfigHttpEnricherName) +: additionalComponents
    )
    .build()

}
