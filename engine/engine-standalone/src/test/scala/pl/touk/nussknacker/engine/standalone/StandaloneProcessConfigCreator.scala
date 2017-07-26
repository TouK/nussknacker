package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicInteger

import argonaut.{DecodeJson, Parse}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.util.LoggingListener
import argonaut.ArgonautShapeless._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.standalone.utils.{JsonStandaloneSourceFactory, StandaloneSinkFactory}

import scala.concurrent.{ExecutionContext, Future}

object StandaloneProcessConfigCreator {
  var processorService = new ThreadLocal[ProcessorService]
}

class StandaloneProcessConfigCreator extends ProcessConfigCreator with LazyLogging {

  val processorService = new ProcessorService

  {
    //this is lame, but statics are not reliable
    StandaloneProcessConfigCreator.processorService.set(processorService)
  }

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map.empty

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "enricherService" -> WithCategories(new EnricherService),
    "processorService" -> WithCategories(processorService)
  )

  implicit val decoder = DecodeJson.derive[Request1]

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-source" -> WithCategories(new JsonStandaloneSourceFactory[Request1])
  )

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new StandaloneSinkFactory)
  )

  override def listeners(config: Config): Seq[ProcessListener] = List(LoggingListener)

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(md => new EspExceptionHandler {
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = logger.error("Error", exceptionInfo)
  })

  override def globalProcessVariables(config: Config): Map[String, WithCategories[AnyRef]] = Map.empty

  override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map.empty

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
