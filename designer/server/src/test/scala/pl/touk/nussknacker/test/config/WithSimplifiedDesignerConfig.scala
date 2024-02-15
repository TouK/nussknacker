package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.{Enum, EnumEntry}
import io.restassured.specification.RequestSpecification
import org.scalatest.Suite
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.NuRestAssureExtensions
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.DesignerTestConfigValidator

trait WithSimplifiedDesignerConfig extends WithDesignerConfig {
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

object WithSimplifiedDesignerConfig {
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
        case TestProcessingType.Streaming => TestCategory.Category1
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

    private[WithSimplifiedDesignerConfig] lazy val categoryByProcessingType =
      TestProcessingType.values.map { processingType =>
        (processingType, TestProcessingType.categoryBy(processingType))
      }.toMap

  }

}

trait WithSimplifiedConfigRestAssuredUsersExtensions extends NuRestAssureExtensions {
  this: WithSimplifiedDesignerConfig =>

  implicit class UsersBasicAuth[T <: RequestSpecification](requestSpecification: T) {

    def basicAuthAdmin(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("admin", "admin")

    def basicAuthAllPermUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("allpermuser", "allpermuser")

    def basicAuthUnknownUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("unknownuser", "wrongcredentials")
  }

}
