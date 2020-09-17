package pl.touk.nussknacker.engine.process.functional

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ProcessVersion, Service}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.api.{AdditionalTypeInformationProvider, NkGlobalParameters}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import org.apache.flink.api.scala._

import scala.reflect.ClassTag

class TypeBenchmarkingSpec extends FunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("test simple flow") {


    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processorEnd("proc2", "logService", "all" -> "#input")


    val data = List(TestData("aaa", 222), TestData("zz", 444))

    import org.apache.flink.api.scala._
    runForData(process, data)


    MockService.data should have size 2

  }

  private def runForData[T: ClassTag : TypeInformation](process: EspProcess, data: List[T]) = {
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("http://notexist.pl"))

    val env = flinkMiniCluster.createExecutionEnvironment()
    val envConfig = env.getConfig

    //envConfig disableGenericTypes()
    envConfig.disableObjectReuse()

    val creator: ProcessConfigCreator = new EmptyProcessConfigCreator {

      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]]
      = Map("input" -> WithCategories(FlinkSourceFactory.noParam(
        new CollectionSource[T](envConfig, data, None, Typed[T]))))

      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
        "logService" -> WithCategories(new MockService),
        "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService),
        "serviceAcceptingOptionalValue" -> WithCategories(ServiceAcceptingScalaOption)
      )

      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
        "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink))
      )

      override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
        ExceptionHandlerFactory.noParams(_ => RecordingExceptionHandler)

    }

    val modelData = LocalModelData(config, creator)
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), config, ExecutionConfigPreparer.unOptimizedChain(modelData, None))
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)

    MockService.clear()
    SinkForStrings.clear()
    SinkForInts.clear()
    env.executeAndWaitForFinished(process.id)()
  }
}

case class TestData(one: String, data: Long)

class TestDataInformationProvider extends AdditionalTypeInformationProvider {

  override def additionalTypeInformation: Set[TypeInformation[_]] = Set(implicitly[TypeInformation[TestData]])
}