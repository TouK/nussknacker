package pl.touk.nussknacker.ui

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import com.typesafe
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, include}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion

class LoadableConfigBasedNussknackerConfigSpec extends AnyFunSuite {

  test("should throw when required configuration is missing") {
    val config = ConfigFactory
      .parseString(
        """
          |scenarioTypes {
          |  "streaming" {
          |    deploymentConfig {
          |      restUrl: "http://localhost:8081"
          |      typeMissing: "flinkStreaming"
          |    }
          |    modelConfig {
          |      classPath: ["test.jar"]
          |    }
          |    category: "Default"
          |  }
          |}
          |""".stripMargin
      )
      .resolve()

    intercept[typesafe.config.ConfigException] {
      loadableConfigBasedNussknackerConfig(config)
        .loadProcessingTypeConfigs()
        .unsafeRunSync()
    }.getMessage should include("No configuration setting found for key 'deploymentConfig.type'")
  }

  test("should throw when no configuration is provided") {
    val config = ConfigFactory
      .parseString(
        """
          |test {}
          |""".stripMargin
      )
      .resolve()

    intercept[RuntimeException] {
      loadableConfigBasedNussknackerConfig(config)
        .loadProcessingTypeConfigs()
        .unsafeRunSync()
    }.getMessage should include("No scenario types configuration provided")
  }

  test("should load the second config when reloaded") {
    val nussknackerConfig = loadDifferentConfigPerInvocationNussknackerConfig(
      config1 = ConfigFactory
        .parseString(
          """
            |scenarioTypes {
            |  "streaming" {
            |    deploymentConfig {
            |      type: "flinkStreaming"
            |      restUrl: "http://localhost:8081"
            |    }
            |    modelConfig {
            |      classPath: []
            |    }
            |    category: "Default"
            |  }
            |}
            |""".stripMargin
        )
        .resolve(),
      config2 = ConfigFactory
        .parseString(
          """
            |scenarioTypes {
            |  "streaming" {
            |    deploymentConfig {
            |      type: "flinkStreaming"
            |      restUrl: "http://localhost:8081"
            |    }
            |    modelConfig {
            |      classPath: []
            |    }
            |    category: "Default"
            |  },
            |  "streaming2" {
            |    deploymentConfig {
            |      type: "flinkStreaming"
            |      restUrl: "http://localhost:8081"
            |    }
            |    modelConfig {
            |      classPath: []
            |    }
            |    category: "Default"
            |  }
            |}
            |""".stripMargin
        )
        .resolve()
    )

    val processingTypes1 = nussknackerConfig
      .loadProcessingTypeConfigs()
      .unsafeRunSync()

    processingTypes1.keys.toSet shouldBe Set("streaming")

    val processingTypes2 = nussknackerConfig
      .loadProcessingTypeConfigs()
      .unsafeRunSync()

    processingTypes2.keys.toSet shouldBe Set("streaming", "streaming2")
  }

  private def loadableConfigBasedNussknackerConfig(config: Config): LoadableConfigBasedNussknackerConfig = {
    loadableConfigBasedNussknackerConfig(IO.pure(ConfigWithUnresolvedVersion(config)))
  }

  private def loadableConfigBasedNussknackerConfig(
      loadConfig: IO[ConfigWithUnresolvedVersion]
  ): LoadableConfigBasedNussknackerConfig = {
    new LoadableConfigBasedNussknackerConfig(loadConfig)
  }

  private def loadDifferentConfigPerInvocationNussknackerConfig(config1: Config, config2: Config, configs: Config*) = {
    val ref        = Ref.unsafe[IO, Int](0)
    val allConfigs = config1 :: config2 :: configs.toList
    val loadConfig = ref.getAndUpdate(_ + 1).flatMap { idx =>
      allConfigs.lift(idx) match {
        case Some(config) => IO.pure(ConfigWithUnresolvedVersion(config))
        case None         => IO.raiseError(throw new IllegalStateException(s"Cannot load the config more than [$idx]"))
      }
    }
    loadableConfigBasedNussknackerConfig(loadConfig)
  }

}
