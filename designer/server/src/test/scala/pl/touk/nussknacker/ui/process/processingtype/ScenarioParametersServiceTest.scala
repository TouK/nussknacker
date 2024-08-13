package pl.touk.nussknacker.ui.process.processingtype

import cats.Always
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ModelDependencies}
import pl.touk.nussknacker.engine.api.component.{ComponentProvider, DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.process.processingtype.loader.{
  LoadableConfigBasedProcessingTypesConfig,
  ProcessingTypeConfigProviderBasedProcessingTypeDataLoader
}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}

import java.nio.file.Path
import scala.jdk.CollectionConverters._

class ScenarioParametersServiceTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with OptionValues
    with LazyLogging {

  private val oneToOneProcessingType1 = "oneToOneProcessingType1"
  private val oneToOneCategory1       = "OneToOneCategory1"
  private val oneToOneProcessingType2 = "oneToOneProcessingType2"
  private val oneToOneCategory2       = "OneToOneCategory2"

  test("processing type to category in one to one relation") {
    val combinations = Map(
      oneToOneProcessingType1 -> parametersWithCategory(oneToOneCategory1),
      oneToOneProcessingType2 -> parametersWithCategory(oneToOneCategory2),
    )
    ScenarioParametersService.create(combinations).validValue
  }

  test("ambiguous category to processing type mapping detection") {
    val categoryUsedMoreThanOnce = "CategoryUsedMoreThanOnce"
    val scenarioTypeA            = "scenarioTypeA"
    val scenarioTypeB            = "scenarioTypeB"
    val combinations = Map(
      scenarioTypeA           -> parametersWithCategory(categoryUsedMoreThanOnce),
      scenarioTypeB           -> parametersWithCategory(categoryUsedMoreThanOnce),
      oneToOneProcessingType1 -> parametersWithCategory(oneToOneCategory1),
    )

    inside(ScenarioParametersService.create(combinations)) {
      case Invalid(ParametersToProcessingTypeMappingAmbiguousException(unambiguousMapping)) =>
        unambiguousMapping should have size 1
        val (parameters, processingTypes) = unambiguousMapping.head
        parameters.category shouldEqual categoryUsedMoreThanOnce
        processingTypes should contain theSameElementsAs Set(scenarioTypeA, scenarioTypeB)
    }
  }

  test(
    "should detect situation when there is only engine setup with errors for some processing type and category combination"
  ) {
    val engineSetupName = EngineSetupName("foo")
    val combinations = Map(
      "fooProcessingType" -> ScenarioParametersWithEngineSetupErrors(
        ScenarioParameters(
          ProcessingMode.UnboundedStream,
          "fooCategory",
          engineSetupName
        ),
        List("aError")
      )
    )
    inside(ScenarioParametersService.create(combinations)) {
      case Invalid(ProcessingModeCategoryWithInvalidEngineSetupsOnly(invalidParametersCombination)) =>
        invalidParametersCombination should have size 1
        val ((processingMode, category), errors) = invalidParametersCombination.head
        processingMode shouldEqual ProcessingMode.UnboundedStream
        category shouldEqual "fooCategory"
        errors shouldEqual List("aError")
    }
  }

  test("should query processing type based on provided parameters") {
    val aCategory = "aCategory"
    val bCategory = "bCategory"

    val unboundedType = "unboundedType"
    val boundedType   = "boundedType"
    val bCategoryType = "bCategoryType"

    val service = ScenarioParametersService
      .create(
        Map(
          unboundedType ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.UnboundedStream, aCategory, EngineSetupName("aSetup")),
              List.empty
            ),
          boundedType ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.BoundedStream, aCategory, EngineSetupName("aSetup")),
              List.empty
            ),
          bCategoryType ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.UnboundedStream, bCategory, EngineSetupName("aSetup")),
              List.empty
            )
        )
      )
      .validValue

    implicit val user: LoggedUser = TestFactory.adminUser()
    service
      .queryProcessingTypeWithWritePermission(Some(aCategory), Some(ProcessingMode.UnboundedStream), None)
      .validValue shouldEqual unboundedType
    service
      .queryProcessingTypeWithWritePermission(Some(aCategory), Some(ProcessingMode.BoundedStream), None)
      .validValue shouldEqual boundedType
    service.queryProcessingTypeWithWritePermission(Some(bCategory), None, None).validValue shouldEqual bCategoryType
  }

  test(
    "should return correct processing type when there is no category passed and some non visible for user combinations are available"
  ) {
    val categoryWithAccess    = "categoryWithAccess"
    val categoryWithoutAccess = "categoryWithoutAccess"

    val processingTypeWithAccess    = "processingTypeWithAccess"
    val processingTypeWithoutAccess = "processingTypeWithoutAccess"

    val service = ScenarioParametersService
      .create(
        Map(
          processingTypeWithAccess ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.UnboundedStream, categoryWithAccess, EngineSetupName("aSetup")),
              List.empty
            ),
          processingTypeWithoutAccess ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.UnboundedStream, categoryWithoutAccess, EngineSetupName("aSetup")),
              List.empty
            )
        )
      )
      .validValue

    implicit val user: LoggedUser = RealLoggedUser(
      "userWithLimitedAccess",
      "userWithLimitedAccess",
      Map(categoryWithAccess -> Set(Permission.Write))
    )
    service
      .queryProcessingTypeWithWritePermission(
        category = None,
        processingMode = Some(ProcessingMode.UnboundedStream),
        engineSetupName = None
      )
      .validValue shouldEqual processingTypeWithAccess
  }

  test("should return engine errors that are only available for user with write access to the given category") {
    val writeAccessCategory        = "writeAccessCategory"
    val noAccessCategory           = "noAccessCategory"
    val writeAccessEngineSetupName = EngineSetupName("writeAccessEngine")
    val noAccessEngineSetupName    = EngineSetupName("noAccessEngine")
    val noErrorEngineSetupName     = EngineSetupName("noErrorsEngine")
    implicit val user: LoggedUser = RealLoggedUser(
      id = "1",
      username = "user",
      categoryPermissions = Map(writeAccessCategory -> Set(Permission.Write))
    )

    val setupErrors = ScenarioParametersService
      .create(
        Map(
          "writeAccessProcessingType" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, writeAccessCategory, writeAccessEngineSetupName),
            List("aError")
          ),
          "writeAccessProcessingTypeWithoutError" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, writeAccessCategory, noErrorEngineSetupName),
            List.empty
          ),
          "noAccessProcessingType" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, noAccessCategory, noAccessEngineSetupName),
            List("bError")
          ),
          "noAccessProcessingTypeWithoutError" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, noAccessCategory, noErrorEngineSetupName),
            List.empty
          ),
        )
      )
      .validValue
      .engineSetupErrorsWithWritePermission

    setupErrors.get(writeAccessEngineSetupName).value shouldBe List("aError")
    setupErrors.get(noAccessEngineSetupName) shouldBe empty
  }

  test(
    "should return engine errors when user has no access to some category where engine setup was used but has access to some other category with the same engine setup"
  ) {
    val writeAccessCategory                       = "writeAccessCategory"
    val noAccessCategory                          = "noAccessCategory"
    val engineSetupNameUsedForMoreThanOneCategory = EngineSetupName("foo")
    val noErrorEngineSetupName                    = EngineSetupName("noErrorsEngine")
    implicit val user: LoggedUser = RealLoggedUser(
      id = "1",
      username = "user",
      categoryPermissions = Map(writeAccessCategory -> Set(Permission.Write))
    )

    val setupErrors = ScenarioParametersService
      .create(
        Map(
          "writeAccessProcessingType" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              writeAccessCategory,
              engineSetupNameUsedForMoreThanOneCategory
            ),
            List("aError")
          ),
          "writeAccessProcessingTypeNoErrors" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              writeAccessCategory,
              noErrorEngineSetupName
            ),
            List.empty
          ),
          "writeAccessProcessingType2" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              noAccessCategory,
              engineSetupNameUsedForMoreThanOneCategory
            ),
            List("aError")
          ),
          "writeAccessProcessingType2NoErrors" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              noAccessCategory,
              noErrorEngineSetupName
            ),
            List.empty
          ),
        )
      )
      .validValue
      .engineSetupErrorsWithWritePermission

    setupErrors.get(engineSetupNameUsedForMoreThanOneCategory).value shouldBe List("aError")
  }

  // This test need to be run manually - it requires sbt prepareDev step to be run before it and we don't
  // want to introduce dependency between designer module and this step to make it run on CI.
  // Alternatively we could introduce a separate sbt project that would depend on designer and dist/Universal/stage
  // but it seems to be a very heavy solution
  ignore("should allow to run docker image without flink") {
    val resourcesDir            = Path.of(getClass.getResource("/").toURI)
    val designerServerModuleDir = resourcesDir.getParent.getParent.getParent
    val distModuleDir           = designerServerModuleDir.getParent.getParent.resolve("nussknacker-dist")
    val devApplicationConfFile  = distModuleDir.resolve("src/universal/conf/application.conf").toFile
    val fallbackConfig = ConfigFactory.parseMap(
      Map(
        "SCHEMA_REGISTRY_URL" -> "foo"
      ).asJava
    )

    val workPath = designerServerModuleDir.resolve("work")
    logDirectoryStructure(workPath)
    val processingTypeDataReader = new ProcessingTypeConfigProviderBasedProcessingTypeDataLoader(
      new LoadableConfigBasedProcessingTypesConfig(
        Always {
          ConfigWithUnresolvedVersion(ConfigFactory.parseFile(devApplicationConfFile).withFallback(fallbackConfig))
        }
      )
    )

    val processingTypeData = processingTypeDataReader.loadProcessingTypeData(
      processingType =>
        ModelDependencies(
          Map.empty,
          componentId => DesignerWideComponentId(componentId.toString),
          Some(workPath),
          shouldIncludeComponentProvider(processingType, _)
        ),
      _ => TestFactory.deploymentManagerDependencies,
    )
    val parametersService = processingTypeData.getCombined().parametersService

    parametersService.scenarioParametersCombinationsWithWritePermission(TestFactory.adminUser()) shouldEqual List(
      ScenarioParameters(ProcessingMode.UnboundedStream, "Default", EngineSetupName("Flink")),
      ScenarioParameters(ProcessingMode.UnboundedStream, "Default", EngineSetupName("Lite Embedded")),
      ScenarioParameters(ProcessingMode.RequestResponse, "Default", EngineSetupName("Lite Embedded"))
    )
    parametersService.engineSetupErrorsWithWritePermission(TestFactory.adminUser()) shouldEqual Map(
      EngineSetupName("Flink")         -> List("Invalid configuration: missing restUrl"),
      EngineSetupName("Lite Embedded") -> List.empty
    )
  }

  private def logDirectoryStructure(workPath: Path): Unit = {
    def listFiles(path: Path): Unit =
      logger.info(s"$path files: ${Option(path.toFile.list()).map(_.mkString(", ")).getOrElse("<missing>")}")
    List(
      workPath,
      workPath.resolve("components"),
      workPath.resolve("components/flink"),
      workPath.resolve("components/lite"),
      workPath.resolve("components/common"),
      workPath.resolve("model"),
    ).foreach(listFiles)
  }

  // This ugly hack is because of Idea classloader issue, see comment in ClassLoaderModelData
  private def shouldIncludeComponentProvider(processingType: ProcessingType, componentProvider: ComponentProvider) = {
    (
      processingType,
      componentProvider.providerName,
      componentProvider.getClass.getClassLoader.getName == "app"
    ) match {
      case (_, "test", _)                                    => false
      case ("streaming", _, true)                            => false
      case ("streaming-lite-embedded", "requestResponse", _) => false
      case ("request-response-embedded", "kafka", _)         => false
      case _                                                 => true
    }
  }

  private def parametersWithCategory(category: String, errors: List[String] = List.empty) =
    ScenarioParametersWithEngineSetupErrors(
      ScenarioParameters(ProcessingMode.UnboundedStream, category, EngineSetupName("Mock")),
      errors
    )

}
