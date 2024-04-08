package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.{Enum, EnumEntry}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.config.WithBatchDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.DesignerTestConfigValidator

trait WithBatchDesignerConfig extends WithDesignerConfig with BeforeAndAfterAll {
  this: Suite =>

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    validateConsistency()
  }

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/business-cases/batch-designer.conf")
  )

  private def validateConsistency(): Unit = {
    val configValidator = new DesignerTestConfigValidator(designerConfig)
    val processingTypeWithCategories =
      TestCategory.categoryByProcessingType.map { case (k, v) => (k.stringify, v.stringify) }
    configValidator.validateTestDataWithDesignerConfFile(processingTypeWithCategories)
  }

}

object WithBatchDesignerConfig {
  sealed trait TestProcessingType extends EnumEntry

  object TestProcessingType extends Enum[TestProcessingType] {
    case object Batch extends TestProcessingType

    override val values = findValues

    implicit class ProcessingTypeStringify(processingType: TestProcessingType) {

      def stringify: String = processingType match {
        case TestProcessingType.Batch => "batch"
      }

    }

    def categoryBy(processingType: TestProcessingType): TestCategory = {
      processingType match {
        case TestProcessingType.Batch => TestCategory.Category1
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
      categoryByProcessingType
        .map(_.swap)
        .apply(category)
    }

    private[WithBatchDesignerConfig] lazy val categoryByProcessingType =
      TestProcessingType.values.map { processingType =>
        (processingType, TestProcessingType.categoryBy(processingType))
      }.toMap

  }

}
