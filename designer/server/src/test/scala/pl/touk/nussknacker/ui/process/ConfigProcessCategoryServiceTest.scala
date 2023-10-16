package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService.CategoryToProcessingTypeMappingAmbiguousException

import scala.util.{Failure, Try}

class ConfigProcessCategoryServiceTest extends AnyFunSuite with Matchers {

  private val oneToOneProcessingType = "oneToOneProcessingType"
  private val oneToOneCategory       = "OneToOneCategory"

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
         |  $oneToOneCategory: $oneToOneProcessingType
         |  $oneToManyCategory1: $oneToManyProcessingType
         |  $oneToManyCategory2: $oneToManyProcessingType
         |}
         |scenarioTypes {
         |  $oneToOneProcessingType {
         |    $processingTypeBasicConfig
         |  }
         |  $oneToManyProcessingType {
         |    $processingTypeBasicConfig
         |  }
         |  $oneToManyProcessingType {
         |    $processingTypeBasicConfig
         |  }
         |}
         |""".stripMargin
    val config          = ConfigFactory.parseString(configWithLegacyCategoriesConfig)
    val categoryService = TestFactory.createCategoryService(config)

    categoryService.getAllCategories shouldEqual List(oneToManyCategory1, oneToManyCategory2, oneToOneCategory)
    categoryService.getTypeForCategoryUnsafe(oneToOneCategory) shouldEqual oneToOneProcessingType
    verifyOneToMany(categoryService)
  }

  test("categories inside processing types configuration format") {
    val configWithLegacyCategoriesConfig =
      s"""scenarioTypes {
         |  $oneToOneProcessingType {
         |    $processingTypeBasicConfig
         |    categories: [$oneToOneCategory]
         |  }
         |  $oneToManyProcessingType {
         |    $processingTypeBasicConfig
         |    categories: [$oneToManyCategory1, $oneToManyCategory2]  
         |  }
         |}""".stripMargin
    val config          = ConfigFactory.parseString(configWithLegacyCategoriesConfig)
    val categoryService = TestFactory.createCategoryService(config)

    categoryService.getAllCategories shouldEqual List(oneToManyCategory1, oneToManyCategory2, oneToOneCategory)
    categoryService.getTypeForCategoryUnsafe(oneToOneCategory) shouldEqual oneToOneProcessingType
    verifyOneToMany(categoryService)
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
         |    categories: [$categoryUsedMoreThanOnce]
         |  }
         |  $scenarioTypeB {
         |    $processingTypeBasicConfig
         |    categories: [$categoryUsedMoreThanOnce]
         |  }
         |  $oneToOneProcessingType {
         |    $processingTypeBasicConfig
         |    categories: [$oneToOneCategory]
         |  }
         |}""".stripMargin

    val config = ConfigFactory.parseString(invalidConfig)

    val expectedMap = Map(categoryUsedMoreThanOnce -> Set(scenarioTypeA, scenarioTypeB))
    Try(TestFactory.createCategoryService(config)) should matchPattern {
      case Failure(CategoryToProcessingTypeMappingAmbiguousException(`expectedMap`)) =>
    }
  }

  private def verifyOneToMany(categoryService: ProcessCategoryService) = {
    categoryService.getProcessingTypeCategories(oneToManyProcessingType) shouldEqual List(
      oneToManyCategory1,
      oneToManyCategory2
    )
    categoryService.getTypeForCategoryUnsafe(oneToManyCategory1) shouldEqual oneToManyProcessingType
    categoryService.getTypeForCategoryUnsafe(oneToManyCategory2) shouldEqual oneToManyProcessingType
  }

}
