package pl.touk.nussknacker.ui.process.processingtypedata

import _root_.sttp.client3.SttpBackend
import _root_.sttp.client3.akkahttp.AkkaHttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.api.helpers.MockDeploymentManager
import pl.touk.nussknacker.ui.component.DefaultComponentIdProvider
import pl.touk.nussknacker.ui.definition.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

class ProcessingTypeDataReaderSpec extends AnyFunSuite with Matchers {
  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName)
  import system.dispatcher
  implicit val sttpBackend: SttpBackend[Future, Any] = AkkaHttpBackend.usingActorSystem(system)
  implicit val deploymentService: DeploymentService  = null

  private val processingTypeBasicConfig =
    """deploymentConfig {
      |  type: FooDeploymentManager
      |}
      |modelConfig {
      |  classPath: []
      |}""".stripMargin

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

    val provider = ProcessingTypeDataProvider(
      StubbedProcessingTypeDataReader
        .loadProcessingTypeData(ConfigWithUnresolvedVersion(config), TestAdditionalUIConfigProvider)
    )
    val scenarioTypes = provider
      .all(AdminUser("admin", "admin"))

    scenarioTypes.keySet shouldEqual Set("foo")
  }

  test("allow to access to processing type data only users that has read access to associated category") {
    val config = ConfigFactory.parseString(s"""
        |scenarioTypes {
        |  foo {
        |    $processingTypeBasicConfig
        |    category: "foo"
        |  }
        |  bar {
        |    $processingTypeBasicConfig
        |    category: "bar"
        |  }
        |}
        |""".stripMargin)

    val provider = ProcessingTypeDataProvider(
      StubbedProcessingTypeDataReader
        .loadProcessingTypeData(ConfigWithUnresolvedVersion(config), TestAdditionalUIConfigProvider)
    )

    val fooCategoryUser = LoggedUser("fooCategoryUser", "fooCategoryUser", Map("foo" -> Set(Permission.Read)))

    provider.forType("foo")(fooCategoryUser)
    an[UnauthorizedError] shouldBe thrownBy {
      provider.forType("bar")(fooCategoryUser)
    }
    provider.all(fooCategoryUser).keys should contain theSameElementsAs List("foo")

    val mappedProvider = provider.mapValues(_ => ())
    mappedProvider.forType("foo")(fooCategoryUser)
    an[UnauthorizedError] shouldBe thrownBy {
      mappedProvider.forType("bar")(fooCategoryUser)
    }
    mappedProvider.all(fooCategoryUser).keys should contain theSameElementsAs List("foo")
  }

  object StubbedProcessingTypeDataReader extends ProcessingTypeDataReader {

    override protected def createProcessingTypeData(
        processingType: ProcessingType,
        typeConfig: ProcessingTypeConfig,
        additionalUIConfigProvider: AdditionalUIConfigProvider
    )(
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
        typeConfig.category
      )
    }

    override protected def createCombinedData(
        valueMap: Map[ProcessingType, ProcessingTypeData]
    ): CombinedProcessingTypeData = {
      CombinedProcessingTypeData(
        statusNameToStateDefinitionsMapping = Map.empty,
        componentIdProvider = new DefaultComponentIdProvider({ (_, _) => None }),
        categoryService = ConfigProcessCategoryService(valueMap.mapValuesNow(_.category))
      )
    }

  }

}
