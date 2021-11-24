package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.exception.{NuExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.flink.util.exception.TestExceptionConsumerProvider.typeName
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.ClassLoaderWithServices

import scala.collection.JavaConverters._

class ConfigurableExceptionhandlerSpec extends FunSuite with Matchers {

  private val config = ConfigFactory.parseMap(Map[String, Any](
    "exceptionHandler.type" -> typeName,
    "exceptionHandler.param1" -> "param",
    //it's difficult to mock RuntimeContext for metrics so we switch it off..
    "exceptionHandler.withRateMeter" -> false,
    "restartStrategy.default.strategy" -> "fixed-delay",
    "restartStrategy.default.delay" -> "10 ms",
    "restartStrategy.default.attempts" -> 10
  ).asJava)

  private val metaData = MetaData("processId", StreamMetaData())

  private def configurableExceptionHandler = ClassLoaderWithServices.withCustomServices(List((classOf[FlinkEspExceptionConsumerProvider],
    classOf[TestExceptionConsumerProvider]))) { loader =>
    new ConfigurableExceptionHandler(metaData, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming), loader)
  }


  test("should load strategy from configuration") {
    configurableExceptionHandler.restartStrategy shouldBe RestartStrategies.fixedDelayRestart(10, 10)
  }

  test("should use handler from configuration") {
    val info = new NuExceptionInfo[NonTransientException](None, NonTransientException("", ""), Context(""))

    configurableExceptionHandler.handle(info)
    TestExceptionConsumerProvider.threadLocal.get() shouldBe (metaData, config.getConfig("exceptionHandler"), info)
  }

}

object TestExceptionConsumerProvider {

  val threadLocal = new ThreadLocal[(MetaData, Config, NuExceptionInfo[NonTransientException])]

  val typeName = "test1"

}

class TestExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override val name: String = typeName

  override def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer = new FlinkEspExceptionConsumer {

    override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
      TestExceptionConsumerProvider.threadLocal.set((metaData, additionalConfig, exceptionInfo))
    }

  }
}
