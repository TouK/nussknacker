package pl.touk.nussknacker.tests.config

import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.{Enum, EnumEntry}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.tests.config.WithSimplifiedNuConfig.TestCategory
import pl.touk.nussknacker.tests.utils.DesignerTestConfigValidator

trait WithSimplifiedNuConfig extends WithDesignerConfig {
  this: Suite =>

  validateConsistency()

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/simple/simple-streaming-use-case-designer.conf")
  )

  private def validateConsistency(): Unit = {
    val configValidator = new DesignerTestConfigValidator(designerConfig)
    val processingTypeWithCategories =
      TestCategory.categoryByProcessingType.map { case (k, v) => (k.stringify, v.stringify) }
    configValidator.validateTestDataWithDesignerConfFile(processingTypeWithCategories)
  }

}

object WithSimplifiedNuConfig {
  sealed trait TestProcessingType extends EnumEntry

  object TestProcessingType extends Enum[TestProcessingType] {
    case object Streaming extends TestProcessingType

    override val values = findValues

    implicit class ProcessingTypeStringify(processingType: TestProcessingType) {

      def stringify: String = processingType match {
        case TestProcessingType.Streaming => "streaming"
      }

    }

    def categoryBy(processingType: TestProcessingType): TestCategory = {
      processingType match {
        case TestProcessingType.Streaming => TestCategory.Default
      }
    }

  }

  sealed trait TestCategory extends EnumEntry

  object TestCategory extends Enum[TestCategory] {
    case object Default extends TestCategory

    override val values = findValues

    implicit class CategoryStringify(category: TestCategory) {

      def stringify: String = category match {
        case Default => "Default"
      }

    }

    def processingTypeBy(category: TestCategory): TestProcessingType = {
      categoryByProcessingType
        .map(_.swap)
        .apply(category)
    }

    private[WithSimplifiedNuConfig] lazy val categoryByProcessingType =
      TestProcessingType.values.map { processingType =>
        (processingType, TestProcessingType.categoryBy(processingType))
      }.toMap

  }

}
