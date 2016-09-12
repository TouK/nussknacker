package pl.touk.esp.engine

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api.InterpreterMode._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.esp.engine.compiledgraph.expression._
import pl.touk.esp.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.esp.engine.compiledgraph.service._
import pl.touk.esp.engine.compiledgraph.variable._
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ServiceInvoker
import pl.touk.esp.engine.util.LoggingListener

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class Interpreter private(services: Map[String, ServiceInvoker],
                          lazyEvaluationTimeout: FiniteDuration,
                          listeners: Seq[ProcessListener] = Seq(LoggingListener)) {

  def interpret(node: Node,
                mode: InterpreterMode,
                metaData: MetaData,
                input: Any,
                inputParamName: String = InputParamName)
               (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    val ctx = Context().withVariable(inputParamName, input)
    interpret(node, mode, metaData, ctx)
  }

  def interpret(node: Node,
                mode: InterpreterMode,
                metaData: MetaData,
                ctx: Context)
               (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    implicit val implMode = mode
    implicit val impMetaData = metaData
    interpretNode(node, ctx)
  }

  private def interpretNode(node: Node, ctx: Context)
                           (implicit mode: InterpreterMode, metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] = {
    listeners.foreach(_.nodeEntered(node.id, ctx, metaData, mode))
    (node, mode) match {
      case (Source(_, next), Traverse) =>
        interpretNext(next, ctx)
      case (VariableBuilder(_, varName, fields, next), Traverse) =>
        interpretNext(next, createOrUpdateVariable(ctx, varName, fields))
      case (Processor(_, ref, next), Traverse) =>
        invoke(ref, ctx).flatMap {
          case ValueWithModifiedContext(_, newCtx) => interpretNext(next, newCtx)
        }
      case (EndingProcessor(id, ref), Traverse) =>
        invoke(ref, ctx).map {
          case ValueWithModifiedContext(output, newCtx) =>
            InterpretationResult(EndReference(id), output, newCtx)
        }
      case (Enricher(_, ref, outName, next), Traverse) =>
        invoke(ref, ctx).flatMap {
          case ValueWithModifiedContext(out, newCtx) =>
            interpretNext(next, newCtx.withVariable(outName, out))
        }
      case (Filter(_, expression, nextTrue, nextFalse), Traverse) =>
        val valueWithModifiedContext = evaluate[Boolean](expression, ctx)
        if (valueWithModifiedContext.value)
          interpretNext(nextTrue, valueWithModifiedContext.context)
        else
          interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
      case (Switch(_, expression, exprVal, nexts, defaultNext), Traverse) =>
        val valueWithModifiedContext = evaluate[Any](expression, ctx)
        val newCtx = valueWithModifiedContext.context.withVariable(exprVal, valueWithModifiedContext.value)
        nexts.foldLeft((newCtx, Option.empty[Next])) {
          case ((accCtx, None), casee) =>
            val valueWithModifiedContext = evaluate[Boolean](casee.expression, accCtx)
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
      case (agg: Aggregate, Traverse) =>
        interpretNext(agg.next, ctx)
      case (agg: Aggregate, AggregateKeyExpression) =>
        Future.successful(InterpretationResult(EndReference(agg.id), evaluate(agg.keyExpression, ctx)))
      case (agg: Aggregate, AggregateTriggerExpression) =>
        Future.successful(InterpretationResult(
          EndReference(agg.id),
          agg.triggerExpression
            .map(evaluate[Boolean](_, ctx))
            .getOrElse(throw new IllegalArgumentException("Trigger expression is not defined"))))
      case (Sink(id, optionalExpression), Traverse) =>
        val valueWithModifiedContext = optionalExpression match {
          case Some(expression) =>
            evaluate[Any](expression, ctx)
          case None =>
            ValueWithModifiedContext(outputValue(ctx), ctx)
        }
        Future.successful(InterpretationResult(EndReference(id), valueWithModifiedContext))
      case (_, AggregateKeyExpression | AggregateTriggerExpression) =>
        throw new IllegalArgumentException(s"Mode $mode make no sense for node: ${node.getClass.getName}")
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context)
                                   (implicit mode: InterpreterMode, metaData: MetaData, ec: ExecutionContext): Future[InterpretationResult] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(ctx, metaData))
        Future.successful(InterpretationResult(DeadEndReference(node.id), outputValue(ctx), ctx))
    }
  }

  private def interpretNext(next: Next, ctx: Context)
                           (implicit mode: InterpreterMode, metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] =
    next match {
      case NextNode(node) => interpretNode(node, ctx)
      case PartRef(ref) => Future.successful(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx))
    }

  private def outputValue(ctx: Context) =
    ctx.getOrElse(OutputParamName, new java.util.HashMap[String, Any]())

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])
                                    (implicit ec: ExecutionContext, metaData: MetaData): Context = {
    val contextWithInitialVariable = ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))
    fields.foldLeft(contextWithInitialVariable) {
      case (context, field) =>
        val valueWithModifiedContext = evaluate[Any](field.expression, context)
        valueWithModifiedContext.context.modifyVariable[java.util.Map[String, Any]](varName, { m =>
          val newMap = new java.util.HashMap[String, Any](m)
          newMap.put(field.name, valueWithModifiedContext.value)
          newMap
        })
    }
  }

  private def invoke(ref: ServiceRef, ctx: Context)
                    (implicit executionContext: ExecutionContext, metaData: MetaData): Future[ValueWithModifiedContext[Any]] = {
    val (newCtx, preparedParams) = ref.parameters.foldLeft((ctx, Map.empty[String, Any])) {
      case ((accCtx, accParams), param) =>
        val valueWithModifiedContext = evaluate[Any](param.expression, accCtx)
        val newAccParams = accParams + (param.name -> valueWithModifiedContext.value)
        (valueWithModifiedContext.context, newAccParams)

    }
    val resultFuture = ref.invoker.invoke(implicitParams(ctx) ++ preparedParams)
    resultFuture.onComplete { result =>
      listeners.foreach(_.serviceInvoked(ref.id, ctx, metaData, result))
    }
    resultFuture.map { result =>
      ValueWithModifiedContext(result, newCtx)
    }
  }

  private def evaluate[R](expr: Expression, ctx: Context)
                         (implicit ec: ExecutionContext, metaData: MetaData): ValueWithModifiedContext[R] = {
    val lazyValuesProvider = new LazyValuesProviderImpl(
      services = services,
      implicitParams = implicitParams(ctx),
      timeout = lazyEvaluationTimeout
    )
    val valueWithModifiedContext = expr.evaluate[R](ctx, lazyValuesProvider)
    listeners.foreach(_.expressionEvaluated(expr.original, ctx, metaData, valueWithModifiedContext.value))
    valueWithModifiedContext
  }

  private def implicitParams(ctx: Context): Map[String, Any] =
    ctx.variables // maybe properties of variables too?

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"

  def apply(servicesDefs: Map[String, ObjectWithMethodDef],
            lazyEvaluationTimeout: FiniteDuration,
            listeners: Seq[ProcessListener] = Seq(LoggingListener)) = {
    new Interpreter(servicesDefs.mapValues(ServiceInvoker(_)), lazyEvaluationTimeout, listeners)
  }

  private class LazyValuesProviderImpl(services: Map[String, ServiceInvoker],
                                       implicitParams: Map[String, Any],
                                       timeout: FiniteDuration)
                                      (implicit ec: ExecutionContext) extends LazyValuesProvider {

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
      val resultFuture = service.invoke(implicitParams ++ paramsMap)
      // await jest niestety niezbędny tutaj, bo implementacje wyrażeń (spel) nie potrafią przetwarzać asynchronicznie
      Await.result(resultFuture, timeout).asInstanceOf[T]
    }

  }

}