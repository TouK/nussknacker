package pl.touk.nussknacker.ui.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromMap
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.api.EmptyDeploymentCommentSettingsError

import scala.jdk.CollectionConverters._

class FeatureTogglesConfigTest extends AnyFunSuite with Matchers {

  test("should be ok with no deploymentCommentSettings") {
    val config = ConfigFactory
      .parseResources("config/business-cases/category-used-more-than-once-designer.conf")
    val parsedFeatures = FeatureTogglesConfig.create(config)
    parsedFeatures.deploymentCommentSettings shouldBe None
  }

  test("should raise exception when invalid deploymentCommentSettings config") {
    val config = ConfigFactory
      .parseResources("config/business-cases/simple-streaming-use-case-designer.conf")
      .withValue(
        "deploymentCommentSettings",
        fromMap(
          Map[String, String](
            "validationPattern" -> ""
          ).asJava
        )
      )
    intercept[EmptyDeploymentCommentSettingsError] {
      FeatureTogglesConfig.create(config)
    }
  }

}
