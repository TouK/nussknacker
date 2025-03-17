package pl.touk.nussknacker.ui.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromMap
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.ui.api.EmptyDeploymentCommentSettingsError

import scala.jdk.CollectionConverters._

class DesignerConfigTest extends AnyFunSuite with Matchers {

  test("should be ok with no deploymentCommentSettings") {
    val config = ScalaMajorVersionConfig
      .configWithScalaMajorVersion(
        ConfigFactory.parseResources("config/business-cases/category-used-more-than-once-designer.conf")
      )
    val parsedFeatures = DesignerConfig.from(config)
    parsedFeatures.deploymentCommentSettings shouldBe None
  }

  test("should raise exception when invalid deploymentCommentSettings config") {
    val config = ScalaMajorVersionConfig
      .configWithScalaMajorVersion(
        ConfigFactory
          .parseResources("config/business-cases/dev-streaming-use-case-designer.conf")
          .withValue(
            "deploymentCommentSettings",
            fromMap(
              Map[String, String](
                "validationPattern" -> ""
              ).asJava
            )
          )
      )
    intercept[EmptyDeploymentCommentSettingsError] {
      DesignerConfig.from(config)
    }
  }

}
