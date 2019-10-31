package pl.touk.nussknacker.engine.process

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.io.{InputFormat, OutputFormat}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.io.{DiscardingOutputFormat, TextInputFormat, TextOutputFormat}
import org.apache.flink.api.scala.{ExecutionEnvironment, _}
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkInputFormat, FlinkInputFormatFactory, FlinkOutputFormat, NoParamInputFormatFactory}
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.flink.util.source.FlinkCollectionInputFormat
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.CommonTestHelpers.RecordingExceptionHandler
import pl.touk.nussknacker.engine.process.compiler.FlinkBatchProcessCompiler

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object BatchProcessTestHelpers {

  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date, value3Opt: Option[BigDecimal] = None, value3: BigDecimal = 1, intAsAny: Any = 1)

  object processInvoker {

    def invoke(process: EspProcess, data: List[SimpleRecord],
                    processVersion: ProcessVersion = ProcessVersion.empty,
                    parallelism: Int = 1): Unit = {
      val env = ExecutionEnvironment.createLocalEnvironment(parallelism)
      val creator = prepareCreator(env.getConfig, data)
      env.getConfig.disableSysoutLogging

      new FlinkBatchProcessCompiler(creator, ConfigFactory.load())
        .createFlinkProcessRegistrar()
        .register(env, process, processVersion)

      env.execute(process.id)
    }

    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]): ProcessConfigCreator = new ProcessConfigCreator {

      override def services(config: Config): Map[String, WithCategories[Service]] = Map(
        "logService" -> WithCategories(new MockService),
        "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService)
      )

      override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[Any]]] = Map(
        "input" -> WithCategories(new NoParamInputFormatFactory(new FlinkCollectionInputFormat[SimpleRecord](exConfig, data))),
        "textLineSource" -> WithCategories(TextLineSourceFactory)
      )

      override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
        "sinkForStrings" -> WithCategories(SinkFactory.noParam(SinkForStrings)),
        "textLineSink" -> WithCategories(TextLineSinkFactory)
      )

      // TODO: custom data set transformers
      override def customStreamTransformers(config: Config): Map[String, Nothing] = Map.empty

      override def listeners(config: Config): Seq[ProcessListener] = Nil

      override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
        ExceptionHandlerFactory.noParams(_ => RecordingExceptionHandler)


      override def expressionConfig(config: Config): ExpressionConfig = {
        val globalProcessVariables = Map("processHelper" -> WithCategories(ProcessHelper))
        ExpressionConfig(globalProcessVariables, Nil)
      }

      override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map.empty

      override def buildInfo(): Map[String, String] = Map.empty
    }
  }

  val RecordingExceptionHandler = new CommonTestHelpers.RecordingExceptionHandler

  object TextLineSourceFactory extends FlinkInputFormatFactory[String] {

    @MethodToInvoke
    def create(@ParamName("path") path: String): FlinkInputFormat[String] = {
      new TextLineSource(path)
    }

    class TextLineSource(path: String) extends FlinkInputFormat[String] {

      override def toFlink: InputFormat[String, _] = {
        new TextInputFormat(new Path(path))
      }

      override def typeInformation: TypeInformation[String] = implicitly[TypeInformation[String]]

      override def classTag: ClassTag[String] = implicitly[ClassTag[String]]
    }
  }

  object MockService extends WithDataList[Any]

  //data is static, to be able to track, Service is object, to initialize metrics properly...
  class MockService extends Service with TimeMeasuringService {

    val serviceName = "mockService"

    @MethodToInvoke
    def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext): Future[Unit] = {
      measuring(Future.successful {
        MockService.add(all)
      })
    }
  }

  class EnricherWithOpenService extends Service with TimeMeasuringService {

    val serviceName = "enricherWithOpenService"

    var internalVar: String = _

    override def open(jobData: JobData): Unit = {
      super.open(jobData)
      internalVar = "initialized!"
    }

    @MethodToInvoke
    def invoke()(implicit ec: ExecutionContext): Future[String] = {
      measuring(Future.successful {
        internalVar
      })
    }
  }

  case object MonitorEmptySink extends FlinkSink {
    val invocationsCount = new AtomicInteger(0)

    def clear() : Unit = {
      invocationsCount.set(0)
    }
    override def testDataOutput: Option[(Any) => String] = Some(output => output.toString)
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        invocationsCount.getAndIncrement()
      }
    }
  }

  object SinkForStrings extends FlinkOutputFormat with Serializable with WithDataList[String] {
    override def toFlink: OutputFormat[Any] = new DiscardingOutputFormat[Any] {
      override def writeRecord(record: Any): Unit = {
        add(record.toString)
      }
    }

    override def testDataOutput: Option[Any => String] = None
  }

  object TextLineSinkFactory extends SinkFactory {

    @MethodToInvoke
    def create(@ParamName("path") path: String): FlinkOutputFormat = {
      new TextLineSink(path)
    }

    class TextLineSink(path: String) extends FlinkOutputFormat with Serializable {

      override def toFlink: OutputFormat[Any] = new TextOutputFormat[Any](new Path(path))

      override def testDataOutput: Option[Any => String] = None
    }
  }
}
