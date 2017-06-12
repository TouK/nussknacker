package pl.touk.esp.engine

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api.InterpreterMode._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.esp.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.esp.engine.compiledgraph.expression._
import pl.touk.esp.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.esp.engine.compiledgraph.service._
import pl.touk.esp.engine.compiledgraph.variable._
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ServiceInvoker
import pl.touk.esp.engine.util.LoggingListener

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class Interpreter private(services: Map[String, ServiceInvoker],
                          globalVariables: Map[String, Any],
                          lazyEvaluationTimeout: FiniteDuration,
                          listeners: Seq[ProcessListener] = Seq(LoggingListener)) {

  def interpret(node: Node,
                mode: InterpreterMode,
                metaData: MetaData,
                ctx: Context)
               (implicit executor: ExecutionContext): Future[Either[InterpretationResult, EspExceptionInfo[_<:Throwable]]] = {
    implicit val implMode = mode
    implicit val impMetaData = metaData
    tryToInterpretNode(node, ctx).map(Left(_)).recover {
      case ex@NodeIdExceptionWrapper(nodeId, exception) =>
        val exInfo = EspExceptionInfo(Some(nodeId), exception, ctx)
        Right(exInfo)
      case NonFatal(ex) =>
        val exInfo = EspExceptionInfo(None, ex, ctx)
        Right(exInfo)
    }
  }

  private def tryToInterpretNode(node: Node, ctx: Context)
                           (implicit mode: InterpreterMode, metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] = {
    try {
      interpretNode(node, ctx).transform(identity, transform(node.id))
    } catch {
      case NonFatal(ex) => Future.failed(transform(node.id)(ex))
    }
  }

  private def transform(nodeId: String)(ex: Throwable) : Throwable = ex match {
    case ex: NodeIdExceptionWrapper => ex
    case ex: Throwable => NodeIdExceptionWrapper(nodeId, ex)
  }

  private def interpretNode(node: Node, ctx: Context)
                           (implicit mode: InterpreterMode, metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] = {
    implicit val nodeImplicit = node
    listeners.foreach(_.nodeEntered(node.id, ctx, metaData, mode))
    (node, mode) match {
      case (Source(_, next), Traverse) =>
        interpretNext(next, ctx)
      case (VariableBuilder(_, varName, Right(fields), next), Traverse) =>
        interpretNext(next, createOrUpdateVariable(ctx, varName, fields))
      case (VariableBuilder(_, varName, Left(expression), next), Traverse) =>
        val valueWithModifiedContext = evaluate[Any](expression, varName, ctx)
        interpretNext(next, ctx.withVariable(varName, valueWithModifiedContext.value))
      case (SubprocessStart(_, params, next), Traverse) =>

        val (newCtx, vars) = params.foldLeft((ctx, Map[String,Any]())){ case ((newCtx, vars), param) =>
          val valueWithCtx = evaluate[Any](param.expression, param.name, newCtx)
          (valueWithCtx.context, vars + (param.name -> valueWithCtx.value))
        }
        interpretNext(next, newCtx.pushNewContext(vars))
      case (SubprocessEnd(id, next), Traverse) =>
        interpretNext(next, ctx.popContext)

      case (Processor(_, ref, next, false), Traverse) =>
        invoke(ref, ctx).flatMap {
          case ValueWithContext(_, newCtx) => interpretNext(next, newCtx)
        }
      case (Processor(_, ref, next, true), Traverse) => interpretNext(next, ctx)
      case (EndingProcessor(id, ref, false), Traverse) =>
        invoke(ref, ctx).map {
          case ValueWithContext(output, newCtx) =>
            InterpretationResult(EndReference(id), output, newCtx)
        }
      case (EndingProcessor(id, ref, true), Traverse) =>
        //FIXME: null??
        Future.successful(InterpretationResult(EndReference(id), null, ctx))
      case (Enricher(_, ref, outName, next), Traverse) =>
        invoke(ref, ctx).flatMap {
          case ValueWithContext(out, newCtx) =>
            interpretNext(next, newCtx.withVariable(outName, out))
        }
      case (Filter(_, expression, nextTrue, nextFalse, disabled), Traverse) =>
        val valueWithModifiedContext = evaluateExpression[Boolean](expression, ctx)
        if (disabled || valueWithModifiedContext.value)
          interpretNext(nextTrue, valueWithModifiedContext.context)
        else
          interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
      case (Switch(_, expression, exprVal, nexts, defaultNext), Traverse) =>
        val valueWithModifiedContext = evaluateExpression[Any](expression, ctx)
        val newCtx = valueWithModifiedContext.context.withVariable(exprVal, valueWithModifiedContext.value)
        nexts.foldLeft((newCtx, Option.empty[Next])) {
          case ((accCtx, None), casee) =>
            //TODO: jakies inne expressionId??
            val valueWithModifiedContext = evaluateExpression[Boolean](casee.expression, accCtx)
            if (valueWithModifiedContext.value) {
              (valueWithModifiedContext.context, Some(casee.node))
            } else {
              (valueWithModifiedContext.context, None)
            }
          case (found, _) =>
            found
        } match {
          case (accCtx, Some(nextNode)) =>
            interpretNext(nextNode, accCtx)
          case (accCtx, None) =>
            interpretOptionalNext(node, defaultNext, accCtx)
        }
      case (Sink(id, ref, optionalExpression), Traverse) =>
        val valueWithModifiedContext = optionalExpression match {
          case Some(expression) =>
            evaluateExpression[Any](expression, ctx)
          case None =>
            ValueWithContext(outputValue(ctx), ctx)
        }
        listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
        Future.successful(InterpretationResult(EndReference(id), valueWithModifiedContext))
      case (CustomNode(id, parameters, _), CustomNodeExpression(expressionName)) =>
        Future.successful(InterpretationResult(
          NextPartReference(id),
          evaluate(parameters.find(_.name == expressionName)
            .map(_.expression)
            .getOrElse(throw new IllegalArgumentException(s"Parameter $mode is not defined")), expressionName, ctx)))
      case (cust: CustomNode, Traverse) =>
        interpretNext(cust.next, ctx)
      //FIXME: yyyy czy to kiedykolwiek moze nastapic??
      case (cust: SplitNode, Traverse) =>
        throw new IllegalArgumentException(s"Split node encountered, should not happen: $cust")
      case (_, CustomNodeExpression(_)) =>
        throw new IllegalArgumentException(s"Mode $mode make no sense for node: ${node.getClass.getName}")
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context)
                                   (implicit mode: InterpreterMode, metaData: MetaData, ec: ExecutionContext): Future[InterpretationResult] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        Future.successful(InterpretationResult(DeadEndReference(node.id), outputValue(ctx), ctx))
    }
  }

  private def interpretNext(next: Next, ctx: Context)
                           (implicit mode: InterpreterMode, metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] =
    next match {
      case NextNode(node) => tryToInterpretNode(node, ctx)
      case PartRef(ref) => Future.successful(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx))
    }

  //hmm... to tak ma byc?
  private def outputValue(ctx: Context): Any =
  ctx.getOrElse[Any](OutputParamName, new java.util.HashMap[String, Any]())

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])
                                    (implicit ec: ExecutionContext, metaData: MetaData, node: Node): Context = {
    val contextWithInitialVariable = ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))
    fields.foldLeft(contextWithInitialVariable) {
      case (context, field) =>
        val valueWithModifiedContext = evaluate[Any](field.expression, field.name, context)
        valueWithModifiedContext.context.modifyVariable[java.util.Map[String, Any]](varName, { m =>
          val newMap = new java.util.HashMap[String, Any](m)
          newMap.put(field.name, valueWithModifiedContext.value)
          newMap
        })
    }
  }

  private def invoke(ref: ServiceRef, ctx: Context)
                    (implicit executionContext: ExecutionContext, metaData: MetaData, node: Node): Future[ValueWithContext[Any]] = {
    val (newCtx, preparedParams) = ref.parameters.foldLeft((ctx, Map.empty[String, Any])) {
      case ((accCtx, accParams), param) =>
        val valueWithModifiedContext = evaluate[Any](param.expression, param.name, accCtx)
        val newAccParams = accParams + (param.name -> valueWithModifiedContext.value)
        (valueWithModifiedContext.context, newAccParams)

    }
    val resultFuture = ref.invoker.invoke(preparedParams, NodeContext(ctx.id, node.id, ref.id))
    resultFuture.onComplete { result =>
      //TODO: a implicit tez??
      listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
    }
    resultFuture.map { result =>
      ValueWithContext(result, newCtx)
    }
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context)
                                   (implicit ec: ExecutionContext, metaData: MetaData, node: Node): ValueWithContext[R]
  = evaluate(expr, "expression", ctx)

  private def evaluate[R](expr: Expression, expressionId: String, ctx: Context)
                         (implicit ec: ExecutionContext, metaData: MetaData, node: Node): ValueWithContext[R] = {
    val lazyValuesProvider = new LazyValuesProviderImpl(
      services = services,
      timeout = lazyEvaluationTimeout,
      ctx = ctx
    )
    val ctxWithGlobals = ctx.withVariables(globalVariables)
    val valueWithLazyContext = expr.evaluate[R](ctxWithGlobals, lazyValuesProvider)
    listeners.foreach(_.expressionEvaluated(node.id, expressionId, expr.original, ctx, metaData, valueWithLazyContext.value))
    ValueWithContext(valueWithLazyContext.value, ctx.withLazyContext(valueWithLazyContext.lazyContext))
  }

  private case class NodeIdExceptionWrapper(nodeId: String, exception: Throwable) extends Exception

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"

  import pl.touk.esp.engine.util.Implicits._

  def apply(servicesDefs: Map[String, ObjectWithMethodDef],
            globalVariables: Map[String, Any],
            lazyEvaluationTimeout: FiniteDuration,
            listeners: Seq[ProcessListener] = Seq(LoggingListener)) = {
    new Interpreter(servicesDefs.mapValuesNow(ServiceInvoker(_)), globalVariables, lazyEvaluationTimeout, listeners)
  }

  private class LazyValuesProviderImpl(services: Map[String, ServiceInvoker],
                                       timeout: FiniteDuration,
                                       ctx: Context)
                                      (implicit ec: ExecutionContext, node: Node) extends LazyValuesProvider {

    override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): (LazyContext, T) = {
      val paramsMap = params.toMap
      context.get[T](serviceId, paramsMap) match {
        case Some(value) =>
          (context, value)
        case None =>
          val value = evaluateValue[T](serviceId, paramsMap)
          (context.withEvaluatedValue(serviceId, paramsMap, value), value)
      }
    }

    private def evaluateValue[T](serviceId: String, paramsMap: Map[String, Any]): T = {
      val service = services.getOrElse(
        serviceId,
        throw new IllegalArgumentException(s"Service with id: $serviceId doesn't exist"))
      val resultFuture = service.invoke(paramsMap, NodeContext(ctx.id, node.id, serviceId))
      // await jest niestety niezbędny tutaj, bo implementacje wyrażeń (spel) nie potrafią przetwarzać asynchronicznie
      Await.result(resultFuture, timeout).asInstanceOf[T]
    }

  }

}