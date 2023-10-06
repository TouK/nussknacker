package pl.touk.nussknacker.ui.process.processingtypedata

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{
  CombinedProcessingTypeData,
  ConfigWithUnresolvedVersion,
  ProcessingTypeConfig,
  ProcessingTypeData
}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.api.helpers.MockDeploymentManager
import pl.touk.nussknacker.ui.component.DefaultComponentIdProvider
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, ProcessCategoryService}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class ProcessingTypeDataReaderSpec extends AnyFunSuite with Matchers {
  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName)
  import system.dispatcher
  implicit val sttpBackend: SttpBackend[Future, Any] = AkkaHttpBackend.usingActorSystem(system)
  implicit val deploymentService: DeploymentService  = null

  test("load only scenario types assigned to configured categories") {
    val config = ConfigFactory.parseString("""categoriesConfig {
        |  "Default": "foo"
        |}
        |scenarioTypes {
        |  foo {
        |    deploymentConfig {
        |      type: "foo"
        |    }
        |    modelConfig {
        |      classPath: []
        |    }
        |  }
        |  bar {
        |    deploymentConfig {
        |      type: "bar"
        |    }
        |    modelConfig {
        |      classPath: []
        |    }
        |  }
        |}
        |""".stripMargin)

    implicit val categoriesService: ProcessCategoryService = new ConfigProcessCategoryService(config)
    val scenarioTypes = StubbedProcessingTypeDataReader.loadProcessingTypeData(ConfigWithUnresolvedVersion(config)).all

    scenarioTypes.keySet shouldEqual Set("foo")
  }

  object StubbedProcessingTypeDataReader extends ProcessingTypeDataReader {

    override protected def createProcessingTypeData(name: ProcessingType, typeConfig: ProcessingTypeConfig)(
        implicit ec: ExecutionContext,
        actorSystem: ActorSystem,
        sttpBackend: SttpBackend[Future, Any],
        deploymentService: DeploymentService
    ): ProcessingTypeData = {
      ProcessingTypeData(
        new MockDeploymentManager,
        null,
        null,
        null,
        Map.empty,
        Nil,
        ProcessingTypeUsageStatistics(None, None)
      )
    }

    override protected def createCombinedData(
        valueMap: Map[ProcessingType, ProcessingTypeData],
        categoryService: ProcessCategoryService
    ): CombinedProcessingTypeData = {
      CombinedProcessingTypeData(
        statusNameToStateDefinitionsMapping = Map.empty,
        componentIdProvider = new DefaultComponentIdProvider(Map.empty),
      )
    }

  }

}
