package pl.touk.nussknacker.engine.management.batch.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.io.{InputFormat, OutputFormat}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.io.{CollectionInputFormat, TextOutputFormat}
import org.apache.flink.api.scala._
import org.apache.flink.core.fs.Path
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkBatchSource, FlinkBatchSourceFactory, FlinkBatchSink}
import pl.touk.nussknacker.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

import scala.reflect.ClassTag

class BatchTestProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "file-sink" -> WithCategories(FileSinkFactory, "Category1", "Category2")
    )
  }

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[Any]]] = {
    Map(
      "elements-source" -> WithCategories(ElementsSourceFactory, "Category1", "Category2")
    )
  }

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler(_))
}

object ElementsSourceFactory extends FlinkBatchSourceFactory[Any] {

  @MethodToInvoke
  def create(@ParamName("elements") elements: java.util.List[Any]): Source[Any] = {
    new ElementsSource(elements)
  }

  class ElementsSource(elements: java.util.List[Any]) extends FlinkBatchSource[Any] {

    override def toFlink: InputFormat[Any, _] = {
      new CollectionInputFormat[Any](elements, typeInformation.createSerializer(ExecutionEnvironment.getExecutionEnvironment.getConfig))
    }

    override def typeInformation: TypeInformation[Any] = implicitly[TypeInformation[Any]]

    override def classTag: ClassTag[Any] = implicitly[ClassTag[Any]]
  }
}

object FileSinkFactory extends SinkFactory {

  @MethodToInvoke
  def create(@ParamName("path") path: String): Sink = {
    new FileSink(path)
  }

  class FileSink(path: String) extends FlinkBatchSink with Serializable {

    override def toFlink: OutputFormat[Any] = new TextOutputFormat[Any](new Path(path))

    override def testDataOutput: Option[Any => String] = None
  }
}
