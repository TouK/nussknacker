package pl.touk.esp.engine.standalone

import java.util.concurrent.atomic.AtomicInteger

import argonaut.{DecodeJson, Parse}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.esp.engine.api.test.TestDataParser
import pl.touk.esp.engine.util.LoggingListener
import argonaut.ArgonautShapeless._

import scala.concurrent.{ExecutionContext, Future}

object StandaloneProcessConfigCreator {
  var processorService = new ThreadLocal[ProcessorService]
}

class StandaloneProcessConfigCreator extends ProcessConfigCreator with LazyLogging {

  val processorService = new ProcessorService

  {
    //no troche slabe, ale na staticach nie da sie chyba polegac
    StandaloneProcessConfigCreator.processorService.set(processorService)
  }

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map.empty

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "enricherService" -> WithCategories(new EnricherService),
    "processorService" -> WithCategories(processorService)
  )

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-source" -> WithCategories(new Request1SourceFactory)
  )

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new ResponseSink)
  )

  override def listeners(config: Config): Seq[ProcessListener] = List(LoggingListener)

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(md => new EspExceptionHandler {
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = logger.error("Error", exceptionInfo)
  })

  override def globalProcessVariables(config: Config): Map[String, WithCategories[Class[_]]] = Map.empty

  override def buildInfo(): Map[String, String] = Map.empty
}

case class Request1(field1: String, field2: String)
case class Request2(field12: String, field22: String)
case class Request3(field13: String, field23: String)

case class Response(field1: String) extends DisplayableAsJson[Response]


class EnricherService extends Service {
  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Response] = {
    Future.successful(Response("alamakota"))
  }
}

class ProcessorService extends Service {

  val invocationsCount = new AtomicInteger(0)

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    if (collector.collectorEnabled) {
      collector.collect("processor service invoked")
    } else {
      invocationsCount.getAndIncrement()
    }
    Future.successful(())
  }

}

class Request1SourceFactory extends StandaloneSourceFactory[Request1] {
  val decoder = DecodeJson.derive[Request1]

  @MethodToInvoke
  def create(): Source[Request1] = {
    new Source[Request1] {
    }
  }

  override def toObject(obj: Array[Byte]): Request1 = {
    val str = new String(obj)
    decoder.decodeJson(Parse.parse(str).right.get).result.right.get
  }

  override def clazz: Class[_] = classOf[Request1]

  override def testDataParser: Option[TestDataParser[Request1]] = Some(
    new TestDataParser[Request1] {
      override def parseTestData(data: Array[Byte]): List[Request1] = {
        val request1List = new String(data).split("\n").toList
        request1List.map(str => decoder.decodeJson(Parse.parse(str).right.get).result.right.get)
      }
    }
  )
}

class ResponseSink extends SinkFactory {
  @MethodToInvoke
  def invoke(): Sink = new Sink {
    override def testDataOutput: Option[(Any) => String] = Some(_.toString)
  }
}