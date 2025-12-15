package pl.touk.nussknacker.engine.process.exception

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.configuration.{Configuration, RestartStrategyOptions}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.test.ClassLoaderWithServices

import scala.jdk.CollectionConverters._

class FlinkExceptionHandlerSpec extends AnyFunSuite with Matchers {

  private val config = ConfigFactory.parseMap(
    Map[String, Any](
      "exceptionHandler.type"   -> TestExceptionConsumerProvider.typeName,
      "exceptionHandler.param1" -> "param",
      // it's difficult to mock RuntimeContext for metrics so we switch it off..
      "exceptionHandler.withRateMeter"   -> false,
      "restartStrategy.default.strategy" -> "fixed-delay",
      "restartStrategy.default.delay"    -> "10 ms",
      "restartStrategy.default.attempts" -> 11
    ).asJava
  )

  private val metaData = MetaData("processId", StreamMetaData())

  private def configurableExceptionHandler = ClassLoaderWithServices.withCustomServices(
    List((classOf[FlinkEspExceptionConsumerProvider], classOf[TestExceptionConsumerProvider]))
  ) { loader =>
    new FlinkExceptionHandler(
      metaData,
      ModelConfig.parse(config),
      listeners = Nil,
      loader
    )
  }

  test("should load strategy from configuration") {
    val expectedRestartStrategyConfig = Configuration.fromMap(
      Map(
        RestartStrategyOptions.RESTART_STRATEGY.key()                      -> "fixed-delay",
        RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY.key()    -> "10 ms",
        RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS.key() -> "11",
      ).asJava
    )
    configurableExceptionHandler.restartStrategyConfig shouldBe expectedRestartStrategyConfig
  }

  test("should use handler from configuration") {
    val info = NuExceptionInfo(None, new Exception, Context.dummy)

    configurableExceptionHandler.handle(info)
    TestExceptionConsumerProvider.threadLocal.get() shouldBe (metaData, config.getConfig("exceptionHandler"), info)
  }

}

object TestExceptionConsumerProvider {

  val threadLocal = new ThreadLocal[(MetaData, Config, NuExceptionInfo)]

  val typeName = "test1"

}

class TestExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override val name: String = TestExceptionConsumerProvider.typeName

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer =
    (exceptionInfo: NuExceptionInfo) => {
      TestExceptionConsumerProvider.threadLocal.set((metaData, exceptionHandlerConfig, exceptionInfo))
    }

}
