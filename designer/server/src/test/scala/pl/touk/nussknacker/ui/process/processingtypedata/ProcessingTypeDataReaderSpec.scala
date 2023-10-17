package pl.touk.nussknacker.ui.process.processingtypedata

import _root_.sttp.client3.SttpBackend
import _root_.sttp.client3.akkahttp.AkkaHttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.definition.DefaultComponentIdProvider
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.api.helpers.MockDeploymentManager
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

class ProcessingTypeDataReaderSpec extends AnyFunSuite with Matchers {
  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName)
  import system.dispatcher
  implicit val sttpBackend: SttpBackend[Future, Any] = AkkaHttpBackend.usingActorSystem(system)
  implicit val deploymentService: DeploymentService  = null

  test("load only selected scenario type if configured") {
    val config = ConfigFactory.parseString("""
        |selectedScenarioType: foo
        |scenarioTypes {
        |  foo {
        |    deploymentConfig {
        |      type: "foo"
        |    }
        |    modelConfig {
        |      classPath: []
        |    }
        |    categories: ["Default"]
        |  }
        |  bar {
        |    deploymentConfig {
        |      type: "bar"
        |    }
        |    modelConfig {
        |      classPath: []
        |    }
        |    categories: ["Default"]
        |  }
        |}
        |""".stripMargin)

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
        ProcessingTypeUsageStatistics(None, None),
        CategoriesConfig(List.empty)
      )
    }

    override protected def createCombinedData(
        processingTypes: Map[ProcessingType, ProcessingTypeData],
        designerConfig: ConfigWithUnresolvedVersion
    ): CombinedProcessingTypeData = {
      val categoryService =
        ConfigProcessCategoryService(designerConfig.resolved, processingTypes.mapValuesNow(_.categoriesConfig))
      CombinedProcessingTypeData(
        statusNameToStateDefinitionsMapping = Map.empty,
        componentIdProvider = new DefaultComponentIdProvider(Map.empty),
        categoryService = categoryService,
        processingTypeSetupService = ProcessingTypeSetupService(processingTypes, categoryService)
      )
    }

  }

}
