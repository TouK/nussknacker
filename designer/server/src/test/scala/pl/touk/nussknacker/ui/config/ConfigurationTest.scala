package pl.touk.nussknacker.ui.config

import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.utils.domain.TestFactory

import java.net.URI
import java.nio.file.Files
import java.util.UUID

// TODO: We should spit DesignerConfigLoader tests and model ProcessingTypeConfig tests
class ConfigurationTest extends AnyFunSuite with Matchers {

  // warning: can't be val - uses ConfigFactory.load which breaks "should preserve config overrides" test
  private def globalConfig = ConfigWithScalaVersion.TestsConfig

  private def modelData: ModelData = ModelData(
    ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig),
    TestFactory.modelDependencies
  )

  private lazy val modelDataConfig = modelData.modelConfig

  private def classLoader = {
    getClass.getClassLoader
  }

  test("defaultConfig works") {
    DesignerConfigLoader(classLoader)
      .loadDesignerConfig()
      .unsafeRunSync()
      .rawConfig
      .resolved
      .getString("db.driver") shouldBe "org.hsqldb.jdbc.JDBCDriver"
  }

  test("should be possible to config entries defined in default ui config from passed config") {
    val configUri = writeToTemp("foo: ${storageDir}") // storageDir is defined inside defaultDesignerConfig.conf
    withNussknackerLocationsProperty(configUri.toString) {
      val loadedConfig = DesignerConfigLoader(classLoader)
        .loadDesignerConfig()
        .unsafeRunSync()

      loadedConfig.rawConfig.resolved.getString("foo") shouldEqual "./storage"
    }
  }

  test("defaultConfig is not accessible from model") {
    modelDataConfig.hasPath("db.driver") shouldBe false
  }

  test("model config is accessible from modelData") {
    modelDataConfig.getString("scenarioPropertiesConfig.environment.label") shouldBe "Environment"
    globalConfig.hasPath("scenarioPropertiesConfig.environment.label") shouldBe false
  }

  test("Can override model.conf from application config, also substitutions") {
    modelDataConfig.getString("scenarioPropertiesConfig.environment.value") shouldBe "OverriddenByConf"
    // in model.conf it's: ${documentationBase}"filter", in designer.conf we substitute documentationBase only
    modelDataConfig.getString("componentsUiConfig.filter.docsUrl") shouldBe "https://touk.github.io/nussknacker/filter"
  }

  // See SampleModelConfigLoader.
  test("should load config using custom loader") {
    modelDataConfig.getLong("configLoadedMs") shouldBe <(System.currentTimeMillis)
    modelDataConfig.getString("configValueToLoad") shouldBe "someDummyValue"
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

    withNussknackerLocationsProperty(conf1.toString) {
      val result =
        try {
          System.setProperty(randomPropertyName, "I win!")
          DesignerConfigLoader(classLoader)
            .loadDesignerConfig()
            .unsafeRunSync()
        } finally {
          System.getProperties.remove(randomPropertyName)
        }

      result.rawConfig.resolved.getString(randomPropertyName) shouldBe "I win!"
    }
  }

  private def withNussknackerLocationsProperty(propertyValue: String)(f: => Unit): Unit = {
    val locationsProperty = "nussknacker.config.locations"
    val valueBeforeChange = Option(System.getProperty(locationsProperty))
    System.setProperty(locationsProperty, propertyValue)
    try {
      f
    } finally {
      valueBeforeChange
        .map(System.setProperty(locationsProperty, _))
        .getOrElse(System.getProperties.remove(locationsProperty))
    }
  }

  // to be able to run this test:
  // add -Dconfig.override_with_env_vars=true to VM parameters
  // set env variable: CONFIG_FORCE_scenarioTypes_streaming_modelConfig_testProperty=testValue
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
