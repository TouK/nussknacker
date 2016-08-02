package pl.touk.esp.engine.process

import java.util.Date

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import org.apache.flink.streaming.api.scala._
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.compile.ProcessCompiler
import pl.touk.esp.engine.process.util.CollectionSource
import pl.touk.esp.engine.{spel, InterpreterConfig}
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.service.{Parameter, ServiceRef}
import pl.touk.esp.engine.util.sink.ServiceSink

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class FlinkProcessRegistrarSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "aggregate and filter records" in {
    val process = EspProcess(MetaData("proc1"),
      GraphBuilder.source("id", "input")
        .aggregate("agg", "#input.id", 5 seconds, 1 second)
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", ServiceRef("logService", List(Parameter("all", "#distinct(#input.![value2])"))))
        .sink("out", "monitor"))
    val data = List(SimpleRecord("1", 12, "a", new Date(0)), SimpleRecord("1", 15, "b", new Date(1000)),
      SimpleRecord("2", 12, "c", new Date(2000)), SimpleRecord("1", 23, "d", new Date(5000))
    )

    processInvoker.invoke(process, data)

    MockService.data shouldNot be('empty)
    MockService.data(0) shouldBe Map("all" -> Set("a", "b").asJava)
  }


}

object processInvoker {
  def invoke(process: EspProcess, data: List[SimpleRecord]) = {
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()

    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    val monitorSink = new ServiceSink(EmptyService, invocationTimeout = 2 minutes)
    val sinkFactories = Map[String, SinkFactory](
      "monitor" -> SinkFactory.noParam(monitorSink)
    )
    new FlinkProcessRegistrar(
      interpreterConfig = InterpreterConfig(Map("logService" -> MockService)),
      sourceFactories = Map("input" -> SourceFactory.noParam(
        new CollectionSource[SimpleRecord](env.getConfig, data, _.date.getTime))),
      sinkFactories = sinkFactories, processTimeout = 2 minutes
    ).register(env, process)

    env.execute()

  }
}

case class SimpleRecord(@BeanProperty id: String, @BeanProperty value1: Long, @BeanProperty value2: String, date: Date)

object MockService extends Service {

  val data = new ArrayBuffer[Map[String, Any]]

  override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext) = Future {
    data.append(params)
  }
}

object EmptyService extends Service {
  override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext) = Future(())
}

