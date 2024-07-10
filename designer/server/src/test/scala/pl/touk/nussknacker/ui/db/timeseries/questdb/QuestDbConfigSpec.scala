package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class QuestDbConfigSpec extends AnyFunSuite with Matchers {

  test("should parse config") {
    val config = ConfigFactory.parseString("""
        |{
        |  questDbSettings {
        |    enabled: true,
        |    instanceId: "test",
        |    directory: "dir"
        |    tasksExecutionDelay: 10 seconds
        |    retentionDelay: 60 seconds
        |    poolConfig: {
        |      corePoolSize: 1
        |      maxPoolSize: 3
        |      keepAliveTimeInSeconds: 30
        |      queueCapacity: 1
        |    }
        |  }
        |}
        |""".stripMargin)

    QuestDbConfig(config) shouldBe QuestDbConfig.Enabled(
      instanceId = "test",
      directory = Some("dir"),
      tasksExecutionDelay = 10 seconds,
      retentionDelay = 60 seconds,
      poolConfig = QuestDbConfig.QuestDbPoolConfig(
        corePoolSize = 1,
        maxPoolSize = 3,
        keepAliveTimeInSeconds = 30,
        queueCapacity = 1
      )
    )
  }

  test("should return defaults") {
    QuestDbConfig(ConfigFactory.empty()) shouldBe QuestDbConfig.Enabled(
      instanceId = "designer-statistics",
      directory = None,
      tasksExecutionDelay = 30 seconds,
      retentionDelay = 24 hours,
      poolConfig = QuestDbConfig.QuestDbPoolConfig(
        corePoolSize = 2,
        maxPoolSize = 4,
        keepAliveTimeInSeconds = 60,
        queueCapacity = 8
      )
    )
  }

  test("should return disabled config") {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  questDbSettings {
                                             |    enabled: false
                                             |  }
                                             |}
                                             |""".stripMargin)

    QuestDbConfig(config) shouldBe QuestDbConfig.Disabled
  }

}
