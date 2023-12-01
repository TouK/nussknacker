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

  test("load only scenario types assigned to configured categories") {
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
        |    category: "Default"
        |  }
        |  bar {
        |    deploymentConfig {
        |      type: "bar"
        |    }
        |    modelConfig {
        |      classPath: []
        |    }
        |    category: "Default"
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
        CategoriesConfig(typeConfig.category)
      )
    }

    override protected def createCombinedData(
        valueMap: Map[ProcessingType, ProcessingTypeData],
        designerConfig: ConfigWithUnresolvedVersion
    ): CombinedProcessingTypeData = {
      CombinedProcessingTypeData(
        statusNameToStateDefinitionsMapping = Map.empty,
        componentIdProvider = new DefaultComponentIdProvider(Map.empty),
        categoryService =
          ConfigProcessCategoryService(designerConfig.resolved, valueMap.mapValuesNow(_.categoriesConfig))
      )
    }

  }

}
