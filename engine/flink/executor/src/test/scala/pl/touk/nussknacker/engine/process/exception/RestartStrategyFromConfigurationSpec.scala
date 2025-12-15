package pl.touk.nussknacker.engine.process.exception

import com.typesafe.config.ConfigFactory
import org.apache.flink.configuration.{Configuration, RestartStrategyOptions}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}

import scala.jdk.CollectionConverters._

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
      Configuration.fromMap(
        Map(
          RestartStrategyOptions.RESTART_STRATEGY.key()                      -> "fixed-delay",
          RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS.key() -> "10"
        ).asJava
      )
    )

    testStrategy(
      Map(
        "restartStrategy.scenarioProperty" -> "other",
        "restartStrategy.default.strategy" -> "fixed-delay",
        "restartStrategy.default.attempts" -> 10
      ),
      Configuration.fromMap(
        Map(
          RestartStrategyOptions.RESTART_STRATEGY.key()                      -> "fixed-delay",
          RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS.key() -> "10"
        ).asJava
      )
    )

    testStrategy(
      Map(
        "restartStrategy.scenarioProperty"     -> "myStrategy",
        "restartStrategy.default.strategy"     -> "fixed-delay",
        "restartStrategy.default.attempts"     -> 10,
        "restartStrategy.oneStrategy.strategy" -> "disable"
      ),
      Configuration.fromMap(
        Map(
          RestartStrategyOptions.RESTART_STRATEGY.key() -> "disable"
        ).asJava
      )
    )

    testStrategy(
      Map(
        "restartStrategy.scenarioProperty"     -> "myOtherStrategy",
        "restartStrategy.default.strategy"     -> "fixed-delay",
        "restartStrategy.default.attempts"     -> 10,
        "restartStrategy.oneStrategy.strategy" -> "disable"
      ),
      Configuration.fromMap(
        Map(
          RestartStrategyOptions.RESTART_STRATEGY.key()                      -> "fixed-delay",
          RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS.key() -> "10"
        ).asJava
      )
    )
  }

  private def testStrategy(configMap: Map[String, Any], expected: Configuration) = {
    val config = ConfigFactory.parseMap(configMap.asJava)
    RestartStrategyFromConfiguration.readFromConfiguration(config, metaData) shouldBe expected
  }

}
