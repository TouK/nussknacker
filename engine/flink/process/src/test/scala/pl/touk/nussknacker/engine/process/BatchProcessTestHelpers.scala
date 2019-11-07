package pl.touk.nussknacker.engine.process

import java.util.Date

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.io.{InputFormat, OutputFormat}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.io.{DiscardingOutputFormat, TextInputFormat, TextOutputFormat}
import org.apache.flink.api.scala.{ExecutionEnvironment, _}
import org.apache.flink.core.fs.Path
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkBatchSource, FlinkBatchSourceFactory, FlinkBatchSink, NoParamBatchSourceFactory}
import pl.touk.nussknacker.engine.flink.util.source.FlinkCollectionBatchSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.compiler.FlinkBatchProcessCompiler
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

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

    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]): ProcessConfigCreator = new EmptyProcessConfigCreator {

      override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[Any]]] = Map(
        "input" -> WithCategories(new NoParamBatchSourceFactory(new FlinkCollectionBatchSource[SimpleRecord](exConfig, data))),
        "textLineSource" -> WithCategories(TextLineSourceFactory)
      )

      override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
        "sinkForStrings" -> WithCategories(SinkFactory.noParam(SinkForStrings)),
        "textLineSink" -> WithCategories(TextLineSinkFactory)
      )

      // TODO: enrichers
      // TODO: custom data set transformers
      override def customStreamTransformers(config: Config): Map[String, Nothing] = Map.empty

      override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
        ExceptionHandlerFactory.noParams(_ => RecordingExceptionHandler)
    }
  }

  val RecordingExceptionHandler = new CommonTestHelpers.RecordingExceptionHandler

  object TextLineSourceFactory extends FlinkBatchSourceFactory[String] {

    @MethodToInvoke
    def create(@ParamName("path") path: String): FlinkBatchSource[String] = {
      new TextLineSource(path)
    }

    class TextLineSource(path: String) extends FlinkBatchSource[String] {

      override def toFlink: InputFormat[String, _] = {
        new TextInputFormat(new Path(path))
      }

      override def typeInformation: TypeInformation[String] = implicitly[TypeInformation[String]]

      override def classTag: ClassTag[String] = implicitly[ClassTag[String]]
    }
  }

  object SinkForStrings extends FlinkBatchSink with Serializable with WithDataList[String] {
    override def toFlink: OutputFormat[Any] = new DiscardingOutputFormat[Any] {
      override def writeRecord(record: Any): Unit = {
        add(record.toString)
      }
    }

    override def testDataOutput: Option[Any => String] = None
  }

  object TextLineSinkFactory extends SinkFactory {

    @MethodToInvoke
    def create(@ParamName("path") path: String): FlinkBatchSink = {
      new TextLineSink(path)
    }

    class TextLineSink(path: String) extends FlinkBatchSink with Serializable {

      override def toFlink: OutputFormat[Any] = new TextOutputFormat[Any](new Path(path))

      override def testDataOutput: Option[Any => String] = None
    }
  }
}
