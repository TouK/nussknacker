package pl.touk.nussknacker.engine.standalone

import cats.Monad
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.baseengine.api.commonTypes
import pl.touk.nussknacker.engine.baseengine.api.commonTypes._
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.{CustomBaseEngineComponent, CustomComponentContext}
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.baseengine.api.utils.transformers.SingleElementBaseEngineComponent
import pl.touk.nussknacker.engine.baseengine.components.{ProcessSplitter, Union}
import pl.touk.nussknacker.engine.standalone.api.StandaloneSinkFactory
import pl.touk.nussknacker.engine.standalone.utils.customtransformers.StandaloneSorter
import pl.touk.nussknacker.engine.standalone.utils.service.TimeMeasuringService
import pl.touk.nussknacker.engine.standalone.utils.{JsonSchemaStandaloneSourceFactory, JsonStandaloneSourceFactory}
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.service.EnricherContextTransformation

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object StandaloneProcessConfigCreator {
  var processorService = new ThreadLocal[ProcessorService]
}

class StandaloneProcessConfigCreator extends ProcessConfigCreator with LazyLogging {

  val processorService = new ProcessorService

  val eagerEnricher = new EagerEnricherWithOpen

  {
    //this is lame, but statics are not reliable
    StandaloneProcessConfigCreator.processorService.set(processorService)
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "splitter" -> WithCategories(ProcessSplitter),
    "union" -> WithCategories(Union),
    "sorter" -> WithCategories(StandaloneSorter),
    "extractor" -> WithCategories(StandaloneCustomExtractor),
    "customFilter" -> WithCategories(CustomFilter)
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "enricherService" -> WithCategories(new EnricherService),
    "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService),
    "eagerEnricherWithOpen" -> WithCategories(eagerEnricher),
    "processorService" -> WithCategories(processorService),
    "collectingEager" -> WithCategories(CollectingEagerService)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-post-source" -> WithCategories(new JsonStandaloneSourceFactory[Request1]),
    "jsonSchemaSource" -> WithCategories(new JsonSchemaStandaloneSourceFactory)
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new StandaloneSinkFactory),
    "parameterResponse-sink" -> WithCategories(ParameterResponseSinkFactory),
    "failing-sink" -> WithCategories(new FailingSinkFactory())
  )

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List(LoggingListener)

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(md => (exceptionInfo: EspExceptionInfo[_ <: Throwable]) => logger.error("Error", exceptionInfo))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = ExpressionConfig(Map.empty, List.empty)

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = Map.empty

  override def buildInfo(): Map[String, String] = Map.empty
}

@JsonCodec case class Request1(field1: String, field2: String) {

  import scala.collection.JavaConverters._

  def toList: java.util.List[String] = List(field1, field2).asJava
}

case class Request2(field12: String, field22: String)

case class Request3(field13: String, field23: String)

@JsonCodec case class Response(field1: String) extends DisplayJsonWithEncoder[Response]

class EnricherService extends Service {
  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector, contextId: ContextId): Future[Response] = {
    Future.successful(Response("alamakota-" + contextId.value))
  }
}

trait WithLifecycle extends Lifecycle {

  var opened: Boolean = false
  var closed: Boolean = false

  def reset(): Unit = {
    opened = false
    closed = false
  }

  override def open(jobData: JobData): Unit = {
    opened = true
  }

  override def close(): Unit = {
    closed = true
  }

}

class EnricherWithOpenService extends Service with TimeMeasuringService with WithLifecycle {

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Response] = {
    measuring {
      Future.successful(Response(opened.toString))
    }
  }

  override protected def serviceName = "enricherWithOpenService"

}

class EagerEnricherWithOpen extends EagerService with WithLifecycle {

  var list: List[(String, WithLifecycle)] = Nil

  override def open(jobData: JobData): Unit = {
    super.open(jobData)
    list.foreach(_._2.open(jobData))
  }

  override def close(): Unit = {
    super.close()
    list.foreach(_._2.close())
  }

  override def reset(): Unit = synchronized {
    super.reset()
    list = Nil
  }

  @MethodToInvoke
  def invoke(@ParamName("name") name: String, @OutputVariableName varName: String)(implicit nodeId: NodeId): ContextTransformation =
    EnricherContextTransformation(varName, Typed[Response], synchronized {
      val newI: ServiceInvoker with WithLifecycle = new ServiceInvoker with WithLifecycle {
        override def invokeService(params: Map[String, Any])
                                  (implicit ec: ExecutionContext,
                                   collector: ServiceInvocationCollector,
                                   contextId: ContextId,
                                   runMode: RunMode): Future[Response] = {
          Future.successful(Response(opened.toString))
        }

      }
      list = (name, newI) :: list
      newI
    })

}

object CollectingEagerService extends EagerService {

  @MethodToInvoke
  def invoke(@ParamName("static") static: String,
             @ParamName("dynamic") dynamic: LazyParameter[String]): ServiceInvoker = new ServiceInvoker {
    override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                         collector: ServiceInvocationCollector,
                                                         contextId: ContextId,
                                                         runMode: RunMode): Future[Any] = {
      collector.collect(s"static-$static-dynamic-${params("dynamic")}", Option(())) {
        Future.successful(())
      }
    }

  }

}

class ProcessorService extends Service {

  val invocationsCount = new AtomicInteger(0)

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    collector.collect("processor service invoked", Option(())) {
      invocationsCount.getAndIncrement()
      Future.successful(())
    }
  }

}

object StandaloneCustomExtractor extends CustomStreamTransformer {

  @MethodToInvoke
  def invoke(@ParamName("expression") expression: LazyParameter[AnyRef],
             @OutputVariableName outputVariableName: String)
            (implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy(ctx => ctx.withVariable(OutputVar.customNode(outputVariableName), expression.returnType))
      .implementedBy(
        new StandaloneCustomExtractor(outputVariableName, expression))
  }

}

class StandaloneCustomExtractor(outputVariableName: String, expression: LazyParameter[AnyRef]) extends CustomBaseEngineComponent {

  override def createTransformation[F[_] : Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): DataBatch => F[ResultType[Result]] = {
    val exprInterpreter: engine.api.Context => Any = context.interpreter.syncInterpretationFunction(expression)
    (ctxs: DataBatch) => {
      val exprResults = ctxs.map(ctx => ctx.withVariable(outputVariableName, exprInterpreter(ctx)))
      continuation(DataBatch(exprResults))
    }
  }
}

object CustomFilter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Unit])
  def invoke(@ParamName("filterExpression") filterExpression: LazyParameter[java.lang.Boolean])
            (implicit nodeId: NodeId) = new CustomFilter(filterExpression)

}

class CustomFilter(filterExpression: LazyParameter[java.lang.Boolean]) extends SingleElementBaseEngineComponent {

  override def createSingleTransformation[F[_] : Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): Context => F[ResultType[Result]] = {
    val exprInterpreter = context.interpreter.syncInterpretationFunction(filterExpression)
    (ctx: Context) => if (exprInterpreter(ctx)) continuation(DataBatch(ctx)) else continuation(DataBatch())
  }
}


object ParameterResponseSinkFactory extends SinkFactory {
  @MethodToInvoke
  def invoke(@ParamName("computed") computed: LazyParameter[String]): Sink = new ParameterResponseSink(computed)

  class ParameterResponseSink(computed: LazyParameter[String]) extends LazyParamSink[AnyRef] {
    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = {
      computed.map(s => s + " withRandomString")
    }
  }

}


private class FailingSinkFactory extends SinkFactory {
  @MethodToInvoke
  def invoke(@ParamName("fail") fail: LazyParameter[java.lang.Boolean]): Sink = new FailingSink(fail)
}

case class SinkException(message: String) extends Exception(message) 

private class FailingSink(val fail: LazyParameter[java.lang.Boolean]) extends LazyParamSink[AnyRef] {
  override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = {
    fail.map { doFail =>
      if (doFail) {
        throw SinkException("FailingSink failed")
      } else {
        "result"
      }
    }
  }

}
