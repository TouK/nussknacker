package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.standalone.api.StandaloneSink
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{EndResult, InterpreterType}
import pl.touk.nussknacker.engine.standalone.api.StandaloneScenarioEngineTypes.StandaloneCustomTransformer
import pl.touk.nussknacker.engine.standalone.api.StandaloneSinkFactory
import pl.touk.nussknacker.engine.standalone.utils.customtransformers.{ProcessSplitter, StandaloneSorter, StandaloneUnion}
import pl.touk.nussknacker.engine.standalone.utils.service.TimeMeasuringService
import pl.touk.nussknacker.engine.standalone.utils.{JsonSchemaStandaloneSourceFactory, JsonStandaloneSourceFactory}
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.service.EnricherContextTransformation

import scala.concurrent.{ExecutionContext, Future}

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
    "union" -> WithCategories(StandaloneUnion),
    "sorter" -> WithCategories(StandaloneSorter),
    "extractor" -> WithCategories(StandaloneCustomExtractor),
    "filterWithLog" -> WithCategories(StandaloneFilterWithLog)
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

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(md => new EspExceptionHandler {
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = logger.error("Error", exceptionInfo)
  })

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

  override protected def serviceName = "enricherWithOpenService"

  @MethodToInvoke
  def invoke()(implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Response] = {
    measuring {
      Future.successful(Response(opened.toString))
    }
  }

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
    list = (name, newI)::list
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

class StandaloneCustomExtractor(outputVariableName: String, expression: LazyParameter[AnyRef]) extends StandaloneCustomTransformer {

  override def createTransformation(outputVariable: Option[String]): CustomTransformation =
    (continuation: InterpreterType[Future], lpi: LazyParameterInterpreter) => {
      val exprInterpreter: engine.api.Context => Any = lpi.syncInterpretationFunction(expression)
      (ctxs: List[engine.api.Context]) => {
        val exprResults = ctxs.map(ctx => ctx.withVariable(outputVariableName, exprInterpreter(ctx)))
        continuation(exprResults)
      }
    }

}

case class StandaloneLogInformation(filterExpression: Boolean)

object StandaloneFilterWithLog extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Unit])
  def invoke(@ParamName("filterExpression") filterExpression: LazyParameter[java.lang.Boolean])
            (implicit nodeId: NodeId): StandaloneCustomTransformer = {
    new StandaloneFilterWithLog(filterExpression, nodeId)
  }

}

class StandaloneFilterWithLog(filterExpression: LazyParameter[java.lang.Boolean], nodeId: NodeId) extends StandaloneCustomTransformer {

  override def createTransformation(outputVariable: Option[String]): CustomTransformation =
    (continuation: InterpreterType[Future], lpi: LazyParameterInterpreter) => {
      implicit val implicitLpi: LazyParameterInterpreter = lpi
      val lazyLogInformation = filterExpression.map(StandaloneLogInformation(_))
      val exprInterpreter: engine.api.Context => StandaloneLogInformation = lpi.syncInterpretationFunction(lazyLogInformation)
      (ctxs: List[engine.api.Context]) => {
        val runInformations = ctxs.map(ctx => (exprInterpreter(ctx), ctx))
        val (pass, skip) = runInformations.partition(_._1.filterExpression)
        val skipped = skip.map {
          case (output, ctx) => EndResult(nodeId.id, ctx, output)
        }
        //FIXME:...
        import scala.concurrent.ExecutionContext.Implicits.global
        continuation(pass.map(_._2)).map(passed => passed.right.map(_ ++ skipped))
      }
    }
}


object ParameterResponseSinkFactory extends SinkFactory {
  @MethodToInvoke
  def invoke(@ParamName("computed") computed: LazyParameter[String]): Sink = new ParameterResponseSink(computed)

  class ParameterResponseSink(computed: LazyParameter[String]) extends StandaloneSink {
    
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

private class FailingSink(val fail: LazyParameter[java.lang.Boolean]) extends StandaloneSink {
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
