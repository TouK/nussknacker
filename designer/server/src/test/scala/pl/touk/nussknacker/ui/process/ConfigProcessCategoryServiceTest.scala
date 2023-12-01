package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.CategoriesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService.{
  CategoryToProcessingTypeMappingAmbiguousException,
  ProcessingTypeToCategoryMappingAmbiguousException
}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataConfigurationReader

import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

class ConfigProcessCategoryServiceTest extends AnyFunSuite with Matchers {

  private val oneToOneProcessingType1 = "oneToOneProcessingType1"
  private val oneToOneCategory1       = "OneToOneCategory1"
  private val oneToOneProcessingType2 = "oneToOneProcessingType2"
  private val oneToOneCategory2       = "OneToOneCategory2"

  private val oneToManyProcessingType = "oneToManyProcessingType"
  private val oneToManyCategory1      = "OneToManyCategory1"
  private val oneToManyCategory2      = "OneToManyCategory2"

  private val processingTypeBasicConfig =
    """deploymentConfig {
      |  type: FooDeploymentManager
      |}
      |modelConfig {
      |  classPath: []
      |}""".stripMargin

  test("legacy categories to processing types mapping") {
    val configWithLegacyCategoriesConfig =
      s"""categoriesConfig {
         |  $oneToOneCategory1: $oneToOneProcessingType1
         |  $oneToOneCategory2: $oneToOneProcessingType2
         |}
         |scenarioTypes {
         |  $oneToOneProcessingType1 {
         |    $processingTypeBasicConfig
         |  }
         |  $oneToOneProcessingType2 {
         |    $processingTypeBasicConfig
         |  }
         |}
         |""".stripMargin
    val config          = ConfigFactory.parseString(configWithLegacyCategoriesConfig)
    val categoryService = TestFactory.createCategoryService(config)

    categoryService.getAllCategories shouldEqual List(oneToOneCategory1, oneToOneCategory2)
    categoryService.getTypeForCategoryUnsafe(oneToOneCategory1) shouldEqual oneToOneProcessingType1
    categoryService.getTypeForCategoryUnsafe(oneToOneCategory2) shouldEqual oneToOneProcessingType2
  }

  test("legacy ambiguous processing type to category mapping") {
    val configWithLegacyCategoriesConfig =
      s"""categoriesConfig {
         |  $oneToManyCategory1: $oneToManyProcessingType
         |  $oneToManyCategory2: $oneToManyProcessingType
         |}
         |scenarioTypes {
         |  $oneToManyProcessingType {
         |    $processingTypeBasicConfig
         |  }
         |  $oneToManyProcessingType {
         |    $processingTypeBasicConfig
         |  }
         |}
         |""".stripMargin
    val config = ConfigFactory.parseString(configWithLegacyCategoriesConfig)

    val expectedMap = Map(oneToManyProcessingType -> Set(oneToManyCategory1, oneToManyCategory2))
    Try(TestFactory.createCategoryService(config)) should matchPattern {
      case Failure(ProcessingTypeToCategoryMappingAmbiguousException(`expectedMap`)) =>
    }
  }

  test("categories inside scenario types configuration format") {
    val configWithCategoriesConfigInsideScenrioTypes =
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
    val config          = ConfigFactory.parseString(configWithCategoriesConfigInsideScenrioTypes)
    val categoryService = TestFactory.createCategoryService(config)

    categoryService.getAllCategories shouldEqual List(oneToOneCategory1, oneToOneCategory2)
    categoryService.getTypeForCategoryUnsafe(oneToOneCategory1) shouldEqual oneToOneProcessingType1
    categoryService.getTypeForCategoryUnsafe(oneToOneCategory2) shouldEqual oneToOneProcessingType2
  }

  test("mixed categories inside scenario types and legacy categories to processing types mapping") {
    val scenarioTypeLegacy = "scenarioTypeLegacy"
    val scenarioTypeNew    = "scenarioTypeNew"
    val scenarioTypeMixed  = "scenarioTypeMixed"
    val categoryLegacy     = "LegacyCategory"
    val categoryNew        = "NewCategory"
    val categoryMixed      = "MixedCategory"
    val configWithMixedCategoriesConfig =
      s"""categoriesConfig {
         |  $categoryLegacy: $scenarioTypeLegacy
         |  $categoryMixed: $scenarioTypeMixed
         |}
         |scenarioTypes {
         |  $scenarioTypeLegacy {
         |    $processingTypeBasicConfig
         |  }
         |  $scenarioTypeNew {
         |    $processingTypeBasicConfig
         |    category: $categoryNew
         |  }
         |  $scenarioTypeMixed {
         |    $processingTypeBasicConfig
         |    category: $categoryMixed
         |  }
         |}""".stripMargin
    val config          = ConfigFactory.parseString(configWithMixedCategoriesConfig)
    val categoryService = TestFactory.createCategoryService(config)

    categoryService.getAllCategories.toSet shouldEqual Set(
      categoryLegacy,
      categoryNew,
      categoryMixed
    )
    categoryService.getTypeForCategoryUnsafe(categoryLegacy) shouldEqual scenarioTypeLegacy
    categoryService.getTypeForCategoryUnsafe(categoryNew) shouldEqual scenarioTypeNew
    categoryService.getTypeForCategoryUnsafe(categoryMixed) shouldEqual scenarioTypeMixed
  }

  // TODO: this is temporary, after fully switch to paradigms we should replace restriction that category
  //       implies processing type with more lax restriction that category + paradigm + engine type
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
      designerConfig.resolved,
      ProcessingTypeDataConfigurationReader
        .readProcessingTypeConfig(designerConfig)
        .mapValuesNow(CategoriesConfig(_))
    )
  }

}
