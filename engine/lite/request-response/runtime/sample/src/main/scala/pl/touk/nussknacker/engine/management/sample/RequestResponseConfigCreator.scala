package pl.touk.nussknacker.engine.management.sample

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponsePostSource, RequestResponseSinkFactory, RequestResponseSourceFactory}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.util.LoggingListener

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class RequestResponseConfigCreator extends ProcessConfigCreator with LazyLogging {

  val category = "ServerRestApi"

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map.empty

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "enricherService" -> WithCategories(new EnricherService, category),
    "timeMeasuringEnricherService" -> WithCategories(new TimeMeasuringEnricherService, category),
    "slowEnricherService" -> WithCategories(new SlowEnricherService, category),
    "processorService" -> WithCategories(new ProcessorService, category)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    "request1-source" -> WithCategories(new Request1SourceFactory, category)
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new RequestResponseSinkFactory, category)
  )

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List(LoggingListener)

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = ExpressionConfig(Map.empty, List.empty)

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = Map.empty

  override def buildInfo(): Map[String, String] = Map.empty
}

//field3 is for checking some quircks of classloading...
@JsonCodec case class Request1(field1: String, field2: String, field3: Option[Request2] = None)
@JsonCodec case class Request2(field12: String, field22: String)
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

  def clear(): Unit = invocationsCount.set(0)
}

class ProcessorService extends Service with Lifecycle {

  private val initialized = new AtomicBoolean(false)

  override def open(engineRuntimeContext: EngineRuntimeContext): Unit = {
    initialized.set(true)
  }

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    if (!initialized.get()) {
      Future.failed(new IllegalArgumentException("I was not initialized!"))
    } else {
      collector.collect("processor service invoked", Option(())) {
        ProcessorService.invocationsCount.getAndIncrement()
        Future.successful(())
      }
    }
  }

}

class Request1SourceFactory extends RequestResponseSourceFactory {

  @MethodToInvoke
  def create(): Source = {
    new RequestResponsePostSource[Request1] with RequestResponseGetSource[Request1] with SourceTestSupport[Request1] {

      override def parse(data: Array[Byte]): Request1 = CirceUtil.decodeJsonUnsafe[Request1](data)

      override def parse(parameters: Map[String, List[String]]): Request1 = {
        def takeFirst(id: String) = parameters.getOrElse(id, List()).headOption.getOrElse("")
        Request1(takeFirst("field1"), takeFirst("field2"))
      }

      override def testDataParser: TestDataParser[Request1] = new NewLineSplittedTestDataParser[Request1] {

        override def parseElement(testElement: String): Request1 = {
          CirceUtil.decodeJsonUnsafe[Request1](testElement, "invalid request")
        }

      }
    }
  }

}
