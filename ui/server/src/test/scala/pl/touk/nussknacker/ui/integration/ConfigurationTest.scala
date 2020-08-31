package pl.touk.nussknacker.ui.integration

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.ui.config.ConfigWithDefaults
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

class ConfigurationTest extends FunSuite with Matchers {

  private val globalConfig = ConfigWithScalaVersion.config

  private val modelDataConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig).toModelData.processConfig

  test("defaultConfig works") {
    ConfigWithDefaults(globalConfig).getString("db.driver") shouldBe "org.hsqldb.jdbc.JDBCDriver"
    ConfigWithDefaults(globalConfig).getString("attachmentsPath") shouldBe "/tmp/attachments"
  }

  test("defaultConfig is not accessible from model") {
    modelDataConfig.hasPath("db.driver") shouldBe false
    modelDataConfig.hasPath("attachmentsPath") shouldBe false
  }

  test("model config is accessible from modelData") {
    modelDataConfig.getString("additionalPropertiesConfig.environment.label") shouldBe "Environment"
    globalConfig.hasPath("additionalPropertiesConfig.environment.label") shouldBe false
  }

  test("Can override model.conf from application config, also substitutions") {
    modelDataConfig.getString("additionalPropertiesConfig.environment.value") shouldBe "OverriddenByConf"
    //in model.conf it's: ${documentationBase}"filter", in ui.conf we substitute documentationBase only
    modelDataConfig.getString("nodes.filter.docsUrl") shouldBe "https://touk.github.io/nussknacker/filter"
  }


}
