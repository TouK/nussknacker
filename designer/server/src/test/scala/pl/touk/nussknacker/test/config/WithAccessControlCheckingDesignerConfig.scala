package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.{Enum, EnumEntry}
import io.restassured.specification.RequestSpecification
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.NuRestAssureExtensions
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory
import pl.touk.nussknacker.test.utils.DesignerTestConfigValidator

// This trait shows setups with multiple categories allowing to verify cases such as access to some category but without access to another one
trait WithAccessControlCheckingDesignerConfig extends WithDesignerConfig with BeforeAndAfterAll {
  this: Suite =>

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    validateConsistency()
  }

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/access-control-checking/multiple-category-designer.conf")
  )

  private def validateConsistency(): Unit = {
    val configValidator = new DesignerTestConfigValidator(designerConfig)
    val processingTypeWithCategories =
      TestCategory.categoryByProcessingType.map { case (k, v) => (k.stringify, v.stringify) }
    configValidator.validateTestDataWithDesignerConfFile(processingTypeWithCategories)
  }

}

object WithAccessControlCheckingDesignerConfig {
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

    private[WithAccessControlCheckingDesignerConfig] lazy val categoryByProcessingType =
      TestProcessingType.values.map { processingType =>
        (processingType, TestProcessingType.categoryBy(processingType))
      }.toMap

  }

}

trait WithAccessControlCheckingConfigRestAssuredUsersExtensions extends NuRestAssureExtensions {
  this: WithAccessControlCheckingDesignerConfig =>

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

  implicit class UsersImpersonation[T <: RequestSpecification](requestSpecification: T) {

    private val impersonationHeader = "Nu-Impersonate-User-Identity"

    def impersonateAdminUser(): RequestSpecification =
      requestSpecification.header(impersonationHeader, "admin")

    def impersonateReaderUser(): RequestSpecification =
      requestSpecification.header(impersonationHeader, "reader")

    def impersonateLimitedReaderUser(): RequestSpecification =
      requestSpecification.header(impersonationHeader, "limitedReader")

    def impersonateWriterUser(): RequestSpecification =
      requestSpecification.header(impersonationHeader, "writer")

    def impersonateLimitedWriterUser(): RequestSpecification =
      requestSpecification.header(impersonationHeader, "limitedWriter")

  }

}
