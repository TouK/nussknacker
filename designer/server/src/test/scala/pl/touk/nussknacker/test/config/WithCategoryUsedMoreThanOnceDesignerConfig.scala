package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.{Enum, EnumEntry}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.DesignerTestConfigValidator

trait WithCategoryUsedMoreThanOnceDesignerConfig extends WithDesignerConfig with BeforeAndAfterAll { this: Suite =>

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    validateConsistency()
  }

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/business-cases/category-used-more-than-once-designer.conf")
  )

  private def validateConsistency(): Unit = {
    val configValidator = new DesignerTestConfigValidator(designerConfig)
    val processingTypeWithCategories =
      TestCategory.categoryByProcessingType.map { case (k, v) => (k.stringify, v.stringify) }
    configValidator.validateTestDataWithDesignerConfFile(processingTypeWithCategories)
  }

}

object WithCategoryUsedMoreThanOnceDesignerConfig {

  sealed trait TestProcessingType extends EnumEntry

  object TestProcessingType extends Enum[TestProcessingType] {
    case object Streaming1 extends TestProcessingType

    case object Streaming2 extends TestProcessingType

    override val values = findValues

    implicit class ProcessingTypeStringify(processingType: TestProcessingType) {

      def stringify: String = processingType match {
        case TestProcessingType.Streaming1 => "streaming1"
        case TestProcessingType.Streaming2 => "streaming2"
      }

    }

    def categoryBy(processingType: TestProcessingType): TestCategory = {
      processingType match {
        case TestProcessingType.Streaming1 => TestCategory.Category1
        case TestProcessingType.Streaming2 => TestCategory.Category1
      }
    }

  }

  sealed trait TestCategory extends EnumEntry

  object TestCategory extends Enum[TestCategory] {
    case object Category1 extends TestCategory

    override val values = findValues

    implicit class CategoryStringify(category: TestCategory) {

      def stringify: String = category match {
        case Category1 => "Category1"
      }

    }

    def processingTypeBy(category: TestCategory): TestProcessingType = {
      // This will not work if we have the same category for different processing types
      categoryByProcessingType
        .map(_.swap)
        .apply(category)
    }

    private[WithCategoryUsedMoreThanOnceDesignerConfig] lazy val categoryByProcessingType =
      TestProcessingType.values.map { processingType =>
        (processingType, TestProcessingType.categoryBy(processingType))
      }.toMap

  }

}
