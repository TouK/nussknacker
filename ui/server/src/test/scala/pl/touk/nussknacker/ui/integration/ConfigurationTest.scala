package pl.touk.nussknacker.ui.integration

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.config.UiConfigLoader
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import java.net.URI
import java.nio.file.Files
import java.util.UUID

class ConfigurationTest extends FunSuite with Matchers {

  // warning: can't be val - uses ConfigFactory.load which breaks "should preserve config overrides" test
  private def globalConfig = ConfigWithScalaVersion.config

  private def modelData: ModelData = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig).toModelData

  private lazy val modelDataConfig = modelData.processConfig

  private def classLoader = {
    getClass.getClassLoader
  }

  test("defaultConfig works") {
    UiConfigLoader.load(globalConfig, classLoader).getString("db.driver") shouldBe "org.hsqldb.jdbc.JDBCDriver"
    UiConfigLoader.load(globalConfig, classLoader).getString("attachmentsPath") shouldBe "/tmp/attachments"
  }

  test("should be possible to config entries defined in default ui config from passed config") {
    val configUri = writeToTemp("foo: ${storageDir}") // storageDir is defined inside defaultUiConfig.conf

    val loadedConfig = UiConfigLoader.load(ConfigFactoryExt.parseConfigFallbackChain(List(configUri), classLoader), classLoader)
    loadedConfig.getString("foo") shouldEqual "./storage"
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
    modelDataConfig.getString("componentsUiConfig.filter.docsUrl") shouldBe "https://touk.github.io/nussknacker/filter"
  }

  // See SampleModelConfigLoader.
  test("should load config using custom loader") {
    modelDataConfig.getLong("configLoadedMs") shouldBe < (System.currentTimeMillis)
    modelDataConfig.getString("duplicatedSignalsTopic") shouldBe "nk.signals"
  }

  // The same mechanism is used with config.override_with_env_var
  // This test must be run separately because ConfigFactory.load() in other tests breaks it
  ignore("should preserve config overrides") {
    val randomPropertyName = UUID.randomUUID().toString

    val content =
      s"""
         |"$randomPropertyName": default
         |""".stripMargin
    val conf1 = writeToTemp(content)

    val result = try {
      System.setProperty(randomPropertyName, "I win!")
      UiConfigLoader.load(ConfigFactoryExt.parseConfigFallbackChain(List(conf1), classLoader), classLoader)
    } finally {
      System.getProperties.remove(randomPropertyName)
    }

    result.getString(randomPropertyName) shouldBe "I win!"
  }

  //to be able to run this test:
  //add -Dconfig.override_with_env_vars=true to VM parameters
  //set env variable: CONFIG_FORCE_scenarioTypes_streaming_modelConfig_testProperty=testValue
  ignore("check if env properties are used/passed") {
    modelDataConfig.getString("testProperty") shouldBe "testValue"
    modelData.inputConfigDuringExecution.config.getString("testProperty") shouldBe "testValue"
  }

  def writeToTemp(content: String): URI = {
    val temp = Files.createTempFile("ConfigurationTest", ".conf")
    temp.toFile.deleteOnExit()
    Files.writeString(temp, content)
    temp.toUri
  }

}
