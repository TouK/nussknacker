package pl.touk.nussknacker.engine.management.batch.sample

import org.apache.flink.api.common.io.{InputFormat, OutputFormat}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.io.{CollectionInputFormat, TextOutputFormat}
import org.apache.flink.api.scala._
import org.apache.flink.core.fs.Path
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkBatchSink, FlinkBatchSource, FlinkBatchSourceFactory}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler

import scala.reflect.ClassTag

class BatchTestProcessConfigCreator extends ProcessConfigCreator {

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "file-sink" -> WithCategories(FileSinkFactory, "Category1", "Category2")
    )
  }

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[Any]]] = {
    Map(
      "elements-source" -> WithCategories(ElementsSourceFactory, "Category1", "Category2")
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map.empty

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map.empty

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = Nil

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = ExpressionConfig.empty

  override def buildInfo(): Map[String, String] = Map.empty

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = Map.empty
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
