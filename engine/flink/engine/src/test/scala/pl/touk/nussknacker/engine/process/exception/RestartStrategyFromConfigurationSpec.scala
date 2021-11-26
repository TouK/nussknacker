package pl.touk.nussknacker.engine.process.exception

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import org.apache.flink.api.common.time.Time
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}

import scala.jdk.CollectionConverters.mapAsJavaMapConverter

class RestartStrategyFromConfigurationSpec extends FunSuite with Matchers {

  private val meta = MetaData("", StreamMetaData(), additionalFields = Some(ProcessAdditionalFields(description = None,
    properties = Map("myStrategy" -> "oneStrategy", "myOtherStrategy" -> ""))))

  test("reads config") {
    testStrategy(Map(
      "restartStrategy.default.strategy" -> "fixed-delay",
      "restartStrategy.default.attempts" -> 10
    ), RestartStrategies.fixedDelayRestart(10, Time.seconds(1)))

    testStrategy(Map(
      "restartStrategy.scenarioProperty" -> "other",
      "restartStrategy.default.strategy" -> "fixed-delay",
      "restartStrategy.default.attempts" -> 10
    ), RestartStrategies.fixedDelayRestart(10, Time.seconds(1)))

    testStrategy(Map(
      "restartStrategy.scenarioProperty" -> "myStrategy",
      "restartStrategy.default.strategy" -> "fixed-delay",
      "restartStrategy.default.attempts" -> 10,
      "restartStrategy.oneStrategy.strategy" -> "disable"
    ), RestartStrategies.noRestart())

    testStrategy(Map(
      "restartStrategy.scenarioProperty" -> "myOtherStrategy",
      "restartStrategy.default.strategy" -> "fixed-delay",
      "restartStrategy.default.attempts" -> 10,
      "restartStrategy.oneStrategy.strategy" -> "disable"
    ), RestartStrategies.fixedDelayRestart(10, Time.seconds(1)))
  }

  private def testStrategy(configMap: Map[String, Any], expected: RestartStrategyConfiguration) = {
    val config = ConfigFactory.parseMap(configMap.asJava)
    RestartStrategyFromConfiguration.readFromConfiguration(config, meta) shouldBe expected
  }

}
