package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicInteger

import argonaut.ArgonautShapeless._
import argonaut.DecodeJson
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.standalone.api.StandaloneGetFactory
import pl.touk.nussknacker.engine.standalone.utils.customtransformers.ProcessSplitter
import pl.touk.nussknacker.engine.standalone.utils.service.TimeMeasuringService
import pl.touk.nussknacker.engine.standalone.utils.{JsonStandaloneSourceFactory, StandaloneContext, StandaloneContextLifecycle, StandaloneSinkFactory}
import pl.touk.nussknacker.engine.util.LoggingListener

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

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "splitter" -> WithCategories(ProcessSplitter)
  )

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "enricherService" -> WithCategories(new EnricherService),
    "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService),
    "processorService" -> WithCategories(processorService),
    "lifecycleService" -> WithCategories(LifecycleService)
  )

  implicit val decoder = DecodeJson.derive[Request1]

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-post-source" -> WithCategories(new JsonStandaloneSourceFactory[Request1]),
    "request1-get-source" -> WithCategories(Request1GetSource)

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

case class Request1(field1: String, field2: String) {
  def toList: List[String] = List(field1, field2)
}
case class Request2(field12: String, field22: String)
case class Request3(field13: String, field23: String)

case class Response(field1: String) extends DisplayableAsJson[Response]

object Request1GetSource extends StandaloneGetFactory[Request1] {

  override def clazz: Class[_] = classOf[Request1]

  override def testDataParser: Option[TestDataParser[Request1]] = None

  override def parse(parameters: Map[String, List[String]]): Request1 = {
    def takeFirst(id: String) = parameters.getOrElse(id, List()).headOption.getOrElse("")
    Request1(takeFirst("field1"), takeFirst("field2"))
  }
}



class EnricherService extends Service {
  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Response] = {
    Future.successful(Response("alamakota"))
  }
}


object LifecycleService extends Service with StandaloneContextLifecycle {

  var opened: Boolean = false
  var closed: Boolean = false


  override def open(context: StandaloneContext): Unit = {
    opened = true
  }

  override def close(): Unit = {
    closed = true
  }

  @MethodToInvoke
  def invoke(): Future[Unit] = {
    Future.successful(())
  }
}

class EnricherWithOpenService extends Service with TimeMeasuringService {

  override protected def serviceName = "enricherWithOpenService"
  var internalVar: String = _

  override def open(): Unit = {
    super.open()
    internalVar = "initialized!"
  }

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Response] = {
    measuring {
      Future.successful(Response(internalVar))
    }
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


