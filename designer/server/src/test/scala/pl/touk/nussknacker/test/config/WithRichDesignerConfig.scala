package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.{Enum, EnumEntry}
import io.restassured.specification.RequestSpecification
import org.scalatest.Suite
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.NuRestAssureExtensions
import pl.touk.nussknacker.test.config.WithRichDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.DesignerTestConfigValidator

trait WithRichDesignerConfig extends WithDesignerConfig {
  this: Suite =>

  validateConsistency()

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/rich/rich-streaming-use-case-designer.conf")
  )

  private def validateConsistency(): Unit = {
    val configValidator = new DesignerTestConfigValidator(designerConfig)
    val processingTypeWithCategories =
      TestCategory.categoryByProcessingType.map { case (k, v) => (k.stringify, v.stringify) }
    configValidator.validateTestDataWithDesignerConfFile(processingTypeWithCategories)
  }

}

object WithRichDesignerConfig {
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
        case TestProcessingType.Streaming2 => TestCategory.Category2
      }
    }

  }

  sealed trait TestCategory extends EnumEntry

  object TestCategory extends Enum[TestCategory] {
    case object Category1 extends TestCategory
    case object Category2 extends TestCategory

    override val values = findValues

    implicit class CategoryStringify(category: TestCategory) {

      def stringify: String = category match {
        case Category1 => "Category1"
        case Category2 => "Category2"
      }

    }

    def processingTypeBy(category: TestCategory): TestProcessingType = {
      categoryByProcessingType
        .map(_.swap)
        .apply(category)
    }

    private[WithRichDesignerConfig] lazy val categoryByProcessingType =
      TestProcessingType.values.map { processingType =>
        (processingType, TestProcessingType.categoryBy(processingType))
      }.toMap

  }

}

trait WithRichConfigRestAssuredUsersExtensions extends NuRestAssureExtensions {
  this: WithRichDesignerConfig =>

  implicit class UsersBasicAuth[T <: RequestSpecification](requestSpecification: T) {

    def basicAuthAdmin(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("admin", "admin")

    def basicAuthReader(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("reader", "reader")

    def basicAuthLimitedReader(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("limitedReader", "limitedReader")

    def basicAuthWriter(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("writer", "writer")

    def basicAuthLimitedWriter(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("limitedWriter", "limitedWriter")

    def basicAuthAllPermUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("allpermuser", "allpermuser")

    def basicAuthUnknownUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("unknownuser", "wrongcredentials")
  }

}
