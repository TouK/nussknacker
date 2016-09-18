package pl.touk.esp.engine.process

import argonaut.PrettyParams
import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.scalatest.FlatSpec
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory}
import pl.touk.esp.engine.api.{ParamName, Service}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.process.ProcessTestHelpers.{EmptySink, SimpleRecord}
import pl.touk.esp.engine.spel
import pl.touk.esp.engine.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.util.source.CollectionSource

import scala.concurrent.Future
import scala.concurrent.duration._

class FlinkProcessMainSpec extends FlatSpec {

  import spel.Implicits._

  it should "be able to compile and serialize services" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .aggregate("agg", "input", "#input.id", 5 seconds, 1 second)
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])")
        .sink("out", "monitor")

    FlinkProcessMain.main(Array(ProcessMarshaller.toJson(process, PrettyParams.spaces2)))
  }

}

object LogService extends Service {
  def invoke(@ParamName("all") all: Any): Future[Unit] = {
    Future.successful(Unit)
  }
}

class SimpleProcessConfigCreator extends ProcessConfigCreator {
  
  import org.apache.flink.streaming.api.scala._

  override def services(config: Config) = Map("logService" -> LogService)

  override def sinkFactories(config: Config) = Map[String, SinkFactory](
    "monitor" -> SinkFactory.noParam(EmptySink)
  )

  override def listeners(config: Config) = List()

  override def customStreamTransformers(config: Config) = Map()

  override def foldingFunctions(config: Config) = Map()

  override def sourceFactories(config: Config) = Map("input" -> SourceFactory.noParam(
    new CollectionSource[SimpleRecord](new ExecutionConfig, List(), Some(new AscendingTimestampExtractor[SimpleRecord] {
      override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
    }))))

  override def exceptionHandlerFactory(config: Config) =
    ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler)

}
