package pl.touk.esp.engine.process.runner

import java.util.Date

import argonaut.PrettyParams
import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SinkFactory, WithCategories}
import pl.touk.esp.engine.api.{ParamName, Service}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.flink.api.process.FlinkSourceFactory
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.flink.util.source.CollectionSource
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.process.ProcessTestHelpers.{EmptySink, SimpleRecord}
import pl.touk.esp.engine.spel

import scala.concurrent.Future

class FlinkProcessMainSpec extends FlatSpec with Matchers with Inside {

  import spel.Implicits._

  val ProcessMarshaller = new ProcessMarshaller

  it should "be able to compile and serialize services" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
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

  override def services(config: Config) = Map("logService" -> WithCategories(LogService, "c1"))

  override def sinkFactories(config: Config) = Map(
    "monitor" -> WithCategories(SinkFactory.noParam(EmptySink), "c2")
  )

  override def listeners(config: Config) = List()

  override def customStreamTransformers(config: Config) = Map()

  override def sourceFactories(config: Config) = Map("input" -> WithCategories(FlinkSourceFactory.noParam(
    new CollectionSource[SimpleRecord](new ExecutionConfig, List(), Some(new AscendingTimestampExtractor[SimpleRecord] {
      override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
    })), Some((csv: String) => {
      val parts = csv.split("\\|")
      SimpleRecord(parts(0), parts(1).toLong, parts(2), new Date(parts(3).toLong), Some(BigDecimal(parts(4))), BigDecimal(parts(5)), parts(6))
    })
  ), "cat2"))

  override def exceptionHandlerFactory(config: Config) =
    ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler)

  override def globalProcessVariables(config: Config): Map[String, WithCategories[Class[_]]] = Map.empty
}
