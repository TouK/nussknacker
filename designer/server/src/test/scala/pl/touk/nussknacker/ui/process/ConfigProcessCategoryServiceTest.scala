package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.tests.TestFactory
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService.CategoryToProcessingTypeMappingAmbiguousException
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataConfigurationReader

import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

class ConfigProcessCategoryServiceTest extends AnyFunSuite with Matchers with OptionValues {

  private val oneToOneProcessingType1 = "oneToOneProcessingType1"
  private val oneToOneCategory1       = "OneToOneCategory1"
  private val oneToOneProcessingType2 = "oneToOneProcessingType2"
  private val oneToOneCategory2       = "OneToOneCategory2"

  private val processingTypeBasicConfig =
    """deploymentConfig {
      |  type: FooDeploymentManager
      |}
      |modelConfig {
      |  classPath: []
      |}""".stripMargin

  test("processing type to category in one to one relation") {
    val config = ConfigFactory.parseString(
      s"""scenarioTypes {
         |  $oneToOneProcessingType1 {
         |    $processingTypeBasicConfig
         |    category: $oneToOneCategory1
         |  }
         |  $oneToOneProcessingType2 {
         |    $processingTypeBasicConfig
         |    category: $oneToOneCategory2
         |  }
         |}""".stripMargin
    )
    val categoryService = TestFactory.createCategoryService(config)

    categoryService.getTypeForCategory(oneToOneCategory1).value shouldEqual oneToOneProcessingType1
    categoryService.getTypeForCategory(oneToOneCategory2).value shouldEqual oneToOneProcessingType2
  }

  // TODO: this is temporary, after fully switch to processing modes we should replace restriction that category
  //       implies processing type with more lax restriction that category + processing mode + engine type
  //       implies processing type
  test("ambiguous category to processing type mapping") {
    val categoryUsedMoreThanOnce = "CategoryUsedMoreThanOnce"
    val scenarioTypeA            = "scenarioTypeA"
    val scenarioTypeB            = "scenarioTypeB"
    val invalidConfig =
      s"""scenarioTypes {
         |  $scenarioTypeA {
         |    $processingTypeBasicConfig
         |    category: $categoryUsedMoreThanOnce
         |  }
         |  $scenarioTypeB {
         |    $processingTypeBasicConfig
         |    category: $categoryUsedMoreThanOnce
         |  }
         |  $oneToOneProcessingType1 {
         |    $processingTypeBasicConfig
         |    category: $oneToOneCategory1
         |  }
         |}""".stripMargin

    val config = ConfigFactory.parseString(invalidConfig)

    val expectedMap = Map(categoryUsedMoreThanOnce -> Set(scenarioTypeA, scenarioTypeB))
    Try(TestFactory.createCategoryService(config)) should matchPattern {
      case Failure(CategoryToProcessingTypeMappingAmbiguousException(`expectedMap`)) =>
    }
  }

  test("Development purpose config") {
    val targetTestClassesDir    = Path.of(getClass.getResource("/").toURI)
    val designerServerModuleDir = targetTestClassesDir.getParent.getParent.getParent
    val distModuleDir           = designerServerModuleDir.getParent.getParent.resolve("nussknacker-dist")
    val devApplicationConfFile  = distModuleDir.resolve("src/universal/conf/dev-application.conf").toFile

    val variables = ConfigFactory.parseMap(
      Map(
        "SQL_ENRICHER_URL"    -> "dummy:5432",
        "SCHEMA_REGISTRY_URL" -> "http://dummy:8888",
        "KAFKA_ADDRESS"       -> "dummy:9092",
        "OPENAPI_SERVICE_URL" -> "http://dummy:5000",
      ).asJava
    )
    val designerConfig = DesignerConfigLoader.load(
      ConfigFactory.parseFile(devApplicationConfFile).withFallback(variables),
      getClass.getClassLoader
    )

    ConfigProcessCategoryService(
      ProcessingTypeDataConfigurationReader
        .readProcessingTypeConfig(designerConfig)
        .mapValuesNow(_.category)
    )
  }

}
