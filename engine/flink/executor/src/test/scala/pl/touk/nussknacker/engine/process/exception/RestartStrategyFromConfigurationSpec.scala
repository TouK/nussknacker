package pl.touk.nussknacker.engine.process.exception

import com.github.ghik.silencer.silent
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import org.apache.flink.api.common.time.Time
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}

import scala.jdk.CollectionConverters._

@silent("deprecated")
class RestartStrategyFromConfigurationSpec extends AnyFunSuite with Matchers {

  private val metaData = MetaData.combineTypeSpecificProperties(
    id = "",
    typeSpecificData = StreamMetaData(),
    additionalFields = ProcessAdditionalFields(
      description = None,
      properties = Map("myStrategy" -> "oneStrategy", "myOtherStrategy" -> ""),
      metaDataType = StreamMetaData.typeName
    )
  )

  test("reads config") {
    testStrategy(
      Map(
        "restartStrategy.default.strategy" -> "fixed-delay",
        "restartStrategy.default.attempts" -> 10
      ),
      RestartStrategies.fixedDelayRestart(10, Time.seconds(1))
    )

    testStrategy(
      Map(
        "restartStrategy.scenarioProperty" -> "other",
        "restartStrategy.default.strategy" -> "fixed-delay",
        "restartStrategy.default.attempts" -> 10
      ),
      RestartStrategies.fixedDelayRestart(10, Time.seconds(1))
    )

    testStrategy(
      Map(
        "restartStrategy.scenarioProperty"     -> "myStrategy",
        "restartStrategy.default.strategy"     -> "fixed-delay",
        "restartStrategy.default.attempts"     -> 10,
        "restartStrategy.oneStrategy.strategy" -> "disable"
      ),
      RestartStrategies.noRestart()
    )

    testStrategy(
      Map(
        "restartStrategy.scenarioProperty"     -> "myOtherStrategy",
        "restartStrategy.default.strategy"     -> "fixed-delay",
        "restartStrategy.default.attempts"     -> 10,
        "restartStrategy.oneStrategy.strategy" -> "disable"
      ),
      RestartStrategies.fixedDelayRestart(10, Time.seconds(1))
    )
  }

  private def testStrategy(configMap: Map[String, Any], expected: RestartStrategyConfiguration) = {
    val config = ConfigFactory.parseMap(configMap.asJava)
    RestartStrategyFromConfiguration.readFromConfiguration(config, metaData) shouldBe expected
  }

}
