package pl.touk.nussknacker.ui.process.processingtype

import cats.data.Validated.valid
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, ProcessingMode}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.mock.{MockDeploymentManager, MockManagerProvider, TestAdditionalUIConfigProvider}
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import java.nio.file.Path

class ProcessingTypeDataReaderSpec extends AnyFunSuite with Matchers {

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
        .loadProcessingTypeData(
          ConfigWithUnresolvedVersion(config),
          _ => TestFactory.deploymentManagerDependencies,
          TestAdditionalUIConfigProvider,
          workingDirectoryOpt = None
        )
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
        .loadProcessingTypeData(
          ConfigWithUnresolvedVersion(config),
          _ => TestFactory.deploymentManagerDependencies,
          TestAdditionalUIConfigProvider,
          workingDirectoryOpt = None
        )
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

    override protected def createDeploymentManagerProvider(
        typeConfig: ProcessingTypeConfig
    ): DeploymentManagerProvider = new MockManagerProvider

    override protected def createProcessingTypeData(
        processingType: ProcessingType,
        processingTypeConfig: ProcessingTypeConfig,
        deploymentManagerProvider: DeploymentManagerProvider,
        deploymentManagerDependencies: DeploymentManagerDependencies,
        engineSetupName: EngineSetupName,
        additionalUIConfigProvider: AdditionalUIConfigProvider,
        workingDirectoryOpt: Option[Path]
    ): ProcessingTypeData = {
      val modelData = LocalModelData(ConfigFactory.empty, List.empty)
      ProcessingTypeData(
        processingType,
        DesignerModelData(modelData, Map.empty, ProcessingMode.UnboundedStream),
        DeploymentData(
          valid(new MockDeploymentManager),
          MetaDataInitializer(StreamMetaData.typeName),
          Map.empty,
          List.empty,
          EngineSetupName("Test engine")
        ),
        processingTypeConfig.category,
        ProcessingTypeUsageStatistics(None, None),
      )
    }

    override protected def createCombinedData(
        valueMap: Map[ProcessingType, ProcessingTypeData]
    ): CombinedProcessingTypeData = {
      CombinedProcessingTypeData(
        statusNameToStateDefinitionsMapping = Map.empty,
        parametersService = ScenarioParametersService.createUnsafe(valueMap.mapValuesNow(_.scenarioParameters))
      )
    }

  }

}
