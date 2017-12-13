package pl.touk.nussknacker.engine.management.sample

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import argonaut.{DecodeJson, Parse}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.standalone.api.{DecodingError, StandaloneGetFactory, StandalonePostFactory, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.standalone.utils.service.TimeMeasuringService
import pl.touk.nussknacker.engine.util.LoggingListener

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class StandaloneProcessConfigCreator extends ProcessConfigCreator with LazyLogging {

  val standaloneCategory = "StandaloneCategory1"

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map.empty

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "enricherService" -> WithCategories(new EnricherService, standaloneCategory),
    "timeMeasuringEnricherService" -> WithCategories(new TimeMeasuringEnricherService, standaloneCategory),
    "slowEnricherService" -> WithCategories(new SlowEnricherService, standaloneCategory),
    "processorService" -> WithCategories(new ProcessorService, standaloneCategory)
  )

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-source" -> WithCategories(new Request1SourceFactory, standaloneCategory)
  )

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new ResponseSink, standaloneCategory)
  )

  override def listeners(config: Config): Seq[ProcessListener] = List(LoggingListener)

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(md => new EspExceptionHandler {
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = logger.error("Error", exceptionInfo)
  })

  override def expressionConfig(config: Config) = ExpressionConfig(Map.empty, List.empty)

  override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map.empty

  override def buildInfo(): Map[String, String] = Map.empty
}

//field3 is for checking some quircks of classloading...
case class Request1(field1: String, field2: String, field3: Option[Request2] = None)
case class Request2(field12: String, field22: String)
case class Request3(field13: String, field23: String)


class EnricherService extends Service {
  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[String] = {
    Future.successful("alamakota")
  }
}

class TimeMeasuringEnricherService extends Service with TimeMeasuringService {
  override protected def serviceName: String = "enricher"

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[String] = {
    measuring{
      Future.successful("alamakota")
    }
  }
}

class SlowEnricherService extends Service with TimeMeasuringService {
  override protected def serviceName: String = "slowEnricher"

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[String] = {
    measuring{
      Thread.sleep(Random.nextInt(500))
      if(Random.nextBoolean()){
        Future.successful("alamakota")
      }else{
        Future.failed(new RuntimeException)
      }
    }
  }
}

object ProcessorService {
  val invocationsCount = new AtomicInteger(0)

  def clear() = invocationsCount.set(0)
}

class ProcessorService extends Service {
  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    if (collector.collectorEnabled) {
      collector.collect("processor service invoked")
    } else {
      ProcessorService.invocationsCount.getAndIncrement()
    }
    Future.successful(())
  }

}

class Request1SourceFactory extends StandalonePostFactory[Request1] with StandaloneGetFactory[Request1] {

  import argonaut.ArgonautShapeless._

  val decoder = DecodeJson.derive[Request1]

  override def parse(data: Array[Byte]): Request1 = {
    val str = new String(data, StandardCharsets.UTF_8)
    decoder.decodeJson(Parse.parse(str).right.get).result match {
      case Left(req) => throw DecodingError(s"Failed to decode on: ${req._1}")
      case Right(req) => req
    }
  }

  override def parse(parameters: Map[String, List[String]]): Request1 = {
    def takeFirst(id: String) = parameters.getOrElse(id, List()).headOption.getOrElse("")
    Request1(takeFirst("field1"), takeFirst("field2"))
  }

  override def clazz: Class[_] = classOf[Request1]

  override def testDataParser: Option[TestDataParser[Request1]] = Some(
    new NewLineSplittedTestDataParser[Request1] {

      override def parseElement(testElement: String): Request1 = {
        decoder.decodeJson(Parse.parse(testElement).right.get).result.right.get
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