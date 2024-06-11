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
        |    enabled: false,
        |    instanceId: "test",
        |    directory: "dir"
        |    flushTaskDelay: 10 seconds
        |    retentionTaskDelay: 60 seconds
        |    poolConfig: {
        |      corePoolSize: 1
        |      maxPoolSize: 3
        |      keepAliveTimeInSeconds: 30
        |      queueCapacity: 1
        |    }
        |  }
        |}
        |""".stripMargin)

    QuestDbConfig(config) shouldBe new QuestDbConfig(
      enabled = false,
      instanceId = "test",
      directory = Some("dir"),
      flushTaskDelay = 10 seconds,
      retentionTaskDelay = 60 seconds,
      poolConfig = QuestDbPoolConfig(
        corePoolSize = 1,
        maxPoolSize = 3,
        keepAliveTimeInSeconds = 30,
        queueCapacity = 1
      )
    )
  }

  test("should return defaults") {
    val config = ConfigFactory.empty()

    QuestDbConfig(config) shouldBe new QuestDbConfig(
      enabled = true,
      instanceId = "designer-statistics",
      directory = None,
      flushTaskDelay = 30 seconds,
      retentionTaskDelay = 24 hours,
      poolConfig = QuestDbPoolConfig(
        corePoolSize = 2,
        maxPoolSize = 4,
        keepAliveTimeInSeconds = 60,
        queueCapacity = 8
      )
    )
  }

}
