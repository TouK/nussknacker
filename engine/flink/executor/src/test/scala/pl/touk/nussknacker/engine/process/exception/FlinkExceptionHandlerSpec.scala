package pl.touk.nussknacker.engine.process.exception

import com.github.ghik.silencer.silent
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.test.ClassLoaderWithServices

import scala.jdk.CollectionConverters._

@silent("deprecated")
class FlinkExceptionHandlerSpec extends AnyFunSuite with Matchers {

  private val config = ConfigFactory.parseMap(
    Map[String, Any](
      "exceptionHandler.type"   -> TestExceptionConsumerProvider.typeName,
      "exceptionHandler.param1" -> "param",
      // it's difficult to mock RuntimeContext for metrics so we switch it off..
      "exceptionHandler.withRateMeter"   -> false,
      "restartStrategy.default.strategy" -> "fixed-delay",
      "restartStrategy.default.delay"    -> "10 ms",
      "restartStrategy.default.attempts" -> 10
    ).asJava
  )

  private val metaData = MetaData("processId", StreamMetaData())

  private def configurableExceptionHandler = ClassLoaderWithServices.withCustomServices(
    List((classOf[FlinkEspExceptionConsumerProvider], classOf[TestExceptionConsumerProvider]))
  ) { loader =>
    new FlinkExceptionHandler(
      metaData,
      ProcessObjectDependencies.withConfig(config),
      listeners = Nil,
      loader
    )
  }

  test("should load strategy from configuration") {
    configurableExceptionHandler.restartStrategy shouldBe RestartStrategies.fixedDelayRestart(10, 10)
  }

  test("should use handler from configuration") {
    val info =
      new NuExceptionInfo[NonTransientException](None, NonTransientException("", ""), Context(""))

    configurableExceptionHandler.handle(info)
    TestExceptionConsumerProvider.threadLocal.get() shouldBe (metaData, config.getConfig("exceptionHandler"), info)
  }

}

object TestExceptionConsumerProvider {

  val threadLocal = new ThreadLocal[(MetaData, Config, NuExceptionInfo[NonTransientException])]

  val typeName = "test1"

}

class TestExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override val name: String = TestExceptionConsumerProvider.typeName

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer =
    (exceptionInfo: NuExceptionInfo[NonTransientException]) => {
      TestExceptionConsumerProvider.threadLocal.set((metaData, exceptionHandlerConfig, exceptionInfo))
    }

}
