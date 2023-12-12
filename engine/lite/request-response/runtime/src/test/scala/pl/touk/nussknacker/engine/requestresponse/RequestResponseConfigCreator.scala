package pl.touk.nussknacker.engine.requestresponse

import cats.Monad
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.lite.api.commonTypes._
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, LiteCustomComponent}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.utils.transformers.SingleElementComponent
import pl.touk.nussknacker.engine.lite.components.requestresponse.CollectTransformer
import pl.touk.nussknacker.engine.requestresponse.utils.JsonRequestResponseSourceFactory
import pl.touk.nussknacker.engine.requestresponse.utils.customtransformers.Sorter
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.service.{EnricherContextTransformation, TimeMeasuringService}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object RequestResponseConfigCreator {
  var processorService = new ThreadLocal[ProcessorService]
}

class RequestResponseConfigCreator extends ProcessConfigCreator with LazyLogging {

  val processorService = new ProcessorService

  val eagerEnricher = new EagerEnricherWithOpen

  {
    // this is lame, but statics are not reliable
    RequestResponseConfigCreator.processorService.set(processorService)
  }

  override def customStreamTransformers(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "sorter"       -> WithCategories.anyCategory(Sorter),
    "extractor"    -> WithCategories.anyCategory(CustomExtractor),
    "customFilter" -> WithCategories.anyCategory(CustomFilter),
    "collect"      -> WithCategories.anyCategory(CollectTransformer)
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      "enricherService"         -> WithCategories.anyCategory(new EnricherService),
      "enricherWithOpenService" -> WithCategories.anyCategory(new EnricherWithOpenService),
      "eagerEnricherWithOpen"   -> WithCategories.anyCategory(eagerEnricher),
      "processorService"        -> WithCategories.anyCategory(processorService),
      "collectingEager"         -> WithCategories.anyCategory(CollectingEagerService)
    )

  override def sourceFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    "request1-post-source"     -> WithCategories.anyCategory(new JsonRequestResponseSourceFactory[Request1]),
    "request-list-post-source" -> WithCategories.anyCategory(new JsonRequestResponseSourceFactory[RequestNumber])
  )

  override def sinkFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink"          -> WithCategories.anyCategory(RequestResponseSinkFactory),
    "parameterResponse-sink" -> WithCategories.anyCategory(ParameterResponseSinkFactory),
    "failing-sink"           -> WithCategories.anyCategory(new FailingSinkFactory())
  )

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List(
    LoggingListener
  )

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig =
    ExpressionConfig(Map.empty, List.empty)

  override def buildInfo(): Map[String, String] = Map.empty
}

@JsonCodec final case class Request1(field1: String, field2: String) {

  import scala.jdk.CollectionConverters._

  def toList: java.util.List[String] = List(field1, field2).asJava
}

final case class Request2(field12: String, field22: String)

final case class Request3(field13: String, field23: String)

@JsonCodec final case class RequestNumber(number: Int) {
  import scala.jdk.CollectionConverters._
  def toList: java.util.List[Int] = (0 to number).asJava
}

@JsonCodec final case class Response(field1: String) extends DisplayJsonWithEncoder[Response]

class EnricherService extends Service {

  @MethodToInvoke
  def invoke()(
      implicit ex: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ScenarioProcessingContextId
  ): Future[Response] = {
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

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    opened = true
  }

  override def close(): Unit = {
    super.close()
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

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
  }

  override protected def serviceName = "enricherWithOpenService"

}

class EagerEnricherWithOpen extends EagerService with WithLifecycle {

  var list: List[(String, WithLifecycle)] = Nil

  override def open(engineRuntimeContext: EngineRuntimeContext): Unit = {
    super.open(engineRuntimeContext)
    list.foreach(_._2.open(engineRuntimeContext))
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
  def invoke(@ParamName("name") name: String, @OutputVariableName varName: String)(
      implicit nodeId: NodeId
  ): ContextTransformation =
    EnricherContextTransformation(
      varName,
      Typed[Response],
      synchronized {
        val newI: ServiceInvoker with WithLifecycle = new ServiceInvoker with WithLifecycle {
          override def invokeService(params: Map[String, Any])(
              implicit ec: ExecutionContext,
              collector: ServiceInvocationCollector,
              contextId: ScenarioProcessingContextId,
              componentUseCase: ComponentUseCase
          ): Future[Response] = {
            Future.successful(Response(opened.toString))
          }

        }
        list = (name, newI) :: list
        newI
      }
    )

}

object CollectingEagerService extends EagerService {

  @MethodToInvoke
  def invoke(
      @ParamName("static") static: String,
      @ParamName("dynamic") dynamic: LazyParameter[String]
  ): ServiceInvoker = new ServiceInvoker {

    override def invokeService(params: Map[String, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        contextId: ScenarioProcessingContextId,
        componentUseCase: ComponentUseCase
    ): Future[Any] = {
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

object CustomExtractor extends CustomStreamTransformer {

  @MethodToInvoke
  def invoke(
      @ParamName("expression") expression: LazyParameter[AnyRef],
      @OutputVariableName outputVariableName: String
  )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy(ctx => ctx.withVariable(OutputVar.customNode(outputVariableName), expression.returnType))
      .implementedBy(new CustomExtractor(outputVariableName, expression))
  }

}

class CustomExtractor(outputVariableName: String, expression: LazyParameter[AnyRef]) extends LiteCustomComponent {

  override def createTransformation[F[_]: Monad, Result](
      continuation: DataBatch => F[ResultType[Result]],
      context: CustomComponentContext[F]
  ): DataBatch => F[ResultType[Result]] = {
    val exprInterpreter: engine.api.ScenarioProcessingContext => Any =
      context.interpreter.syncInterpretationFunction(expression)
    (ctxs: DataBatch) => {
      val exprResults = ctxs.map(ctx => ctx.withVariable(outputVariableName, exprInterpreter(ctx)))
      continuation(DataBatch(exprResults))
    }
  }

}

object CustomFilter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Unit])
  def invoke(@ParamName("filterExpression") filterExpression: LazyParameter[java.lang.Boolean])(
      implicit nodeId: NodeId
  ) = new CustomFilter(filterExpression)

}

class CustomFilter(filterExpression: LazyParameter[java.lang.Boolean]) extends SingleElementComponent {

  override def createSingleTransformation[F[_]: Monad, Result](
      continuation: DataBatch => F[ResultType[Result]],
      context: CustomComponentContext[F]
  ): ScenarioProcessingContext => F[ResultType[Result]] = {
    val exprInterpreter = context.interpreter.syncInterpretationFunction(filterExpression)
    (ctx: ScenarioProcessingContext) =>
      if (exprInterpreter(ctx)) continuation(DataBatch(ctx)) else continuation(DataBatch())
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

private object RequestResponseSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Sink = new LazyParamSink[AnyRef] {
    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] =
      value
  }

}

private class FailingSinkFactory extends SinkFactory {
  @MethodToInvoke
  def invoke(@ParamName("fail") fail: LazyParameter[java.lang.Boolean]): Sink = new FailingSink(fail)
}

final case class SinkException(message: String) extends Exception(message)

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
