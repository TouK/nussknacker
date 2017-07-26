package pl.touk.nussknacker.engine

import cats.Now
import cats.effect.IO
import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.api.InterpreterMode._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.compiledgraph.expression._
import pl.touk.nussknacker.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.nussknacker.engine.compiledgraph.service._
import pl.touk.nussknacker.engine.compiledgraph.variable._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ServiceInvoker
import pl.touk.nussknacker.engine.util.LoggingListener

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class Interpreter private(services: Map[String, ServiceInvoker],
                          globalVariables: Map[String, Any],
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
        createOrUpdateVariable(ctx, varName, fields).flatMap(interpretNext(next, _))
      case (VariableBuilder(_, varName, Left(expression), next), Traverse) =>
        evaluate[Any](expression, varName, ctx).flatMap { valueWithModifiedContext =>
          interpretNext(next, ctx.withVariable(varName, valueWithModifiedContext.value))
        }
      case (SubprocessStart(_, params, next), Traverse) =>
        val futureCtxWithVars = params.foldLeft(Future.successful((ctx, Map[String,Any]()))){ case (futureWithVars, param) =>
          futureWithVars.flatMap { case (newCtx, vars) =>
            evaluate[Any](param.expression, param.name, newCtx).map { valueWithCtx =>
              (valueWithCtx.context, vars + (param.name -> valueWithCtx.value))
            }
          }
        }
        futureCtxWithVars.flatMap { case (newCtx, vars) =>
          interpretNext(next, newCtx.pushNewContext(vars))
        }
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
        evaluateExpression[Boolean](expression, ctx).flatMap { valueWithModifiedContext =>
          if (disabled || valueWithModifiedContext.value)
            interpretNext(nextTrue, valueWithModifiedContext.context)
          else
            interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
        }
      case (Switch(_, expression, exprVal, nexts, defaultNext), Traverse) =>
        val valueWithModifiedContext = evaluateExpression[Any](expression, ctx)
        val newCtx = valueWithModifiedContext.map( vmc =>
          (vmc.context.withVariable(exprVal, vmc.value), Option.empty[Next]))
        nexts.foldLeft(newCtx) { case (acc, casee) =>
          acc.flatMap {
            case (accCtx, None) => evaluateExpression[Boolean](casee.expression, accCtx).map { valueWithModifiedContext =>
              if (valueWithModifiedContext.value) {
                (valueWithModifiedContext.context, Some(casee.node))
              } else {
                (valueWithModifiedContext.context, None)
              }
            }
            case a => Future.successful(a)
          }
        }.flatMap {
          case (accCtx, Some(nextNode)) =>
            interpretNext(nextNode, accCtx)
          case (accCtx, None) =>
            interpretOptionalNext(node, defaultNext, accCtx)
        }
      case (Sink(id, ref, optionalExpression), Traverse) =>
        (optionalExpression match {
          case Some(expression) =>
            evaluateExpression[Any](expression, ctx)
          case None =>
            Future.successful(ValueWithContext(outputValue(ctx), ctx))
        }).map { valueWithModifiedContext =>
          listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
          InterpretationResult(EndReference(id), valueWithModifiedContext)
        }
      case (CustomNode(id, parameters, _), CustomNodeExpression(expressionName)) =>
        val paramToEvaluate = parameters.find(_.name == expressionName)
                             .map(_.expression)
                             .getOrElse(throw new IllegalArgumentException(s"Parameter $mode is not defined"))
         evaluate[Any](paramToEvaluate, expressionName, ctx).map(InterpretationResult(NextPartReference(id), _))
      case (cust: CustomNode, Traverse) =>
        interpretNext(cust.next, ctx)
      //FIXME: can this ever happen?
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

  //hmm... is this OK?
  private def outputValue(ctx: Context): Any =
  ctx.getOrElse[Any](OutputParamName, new java.util.HashMap[String, Any]())

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])
                                    (implicit ec: ExecutionContext, metaData: MetaData, node: Node): Future[Context] = {
    val contextWithInitialVariable = ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))

    fields.foldLeft(Future.successful(contextWithInitialVariable)) {
      case (context, field) =>
        context.flatMap(evaluate[Any](field.expression, field.name, _)).map { valueWithContext =>
          valueWithContext.context.modifyVariable[java.util.Map[String, Any]](varName, { m =>
            val newMap = new java.util.HashMap[String, Any](m)
            newMap.put(field.name, valueWithContext.value)
            newMap
          })
        }
    }
  }

  private def invoke(ref: ServiceRef, ctx: Context)
                    (implicit executionContext: ExecutionContext, metaData: MetaData, node: Node): Future[ValueWithContext[Any]] = {
    ref.parameters.foldLeft(Future.successful((ctx, Map.empty[String, Any]))) {
      case (fut, param) => fut.flatMap { case (accCtx, accParams) =>
        evaluate[Any](param.expression, param.name, accCtx).map { valueWithModifiedContext =>
          val newAccParams = accParams + (param.name -> valueWithModifiedContext.value)
          (valueWithModifiedContext.context, newAccParams)
        }
      }

    }.flatMap { case (newCtx, preparedParams) =>
      val resultFuture = ref.invoker.invoke(preparedParams, NodeContext(ctx.id, node.id, ref.id))
      resultFuture.onComplete { result =>
        //TODO: what about implicit??
        listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
      }
      resultFuture.map(ValueWithContext(_, newCtx))
    }
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context)
                                   (implicit ec: ExecutionContext, metaData: MetaData, node: Node):  Future[ValueWithContext[R]]
  = evaluate(expr, "expression", ctx)

  private def evaluate[R](expr: Expression, expressionId: String, ctx: Context)
                         (implicit ec: ExecutionContext, metaData: MetaData, node: Node): Future[ValueWithContext[R]] = {
    val lazyValuesProvider = new LazyValuesProviderImpl(
      services = services,
      ctx = ctx
    )
    val ctxWithGlobals = ctx.withVariables(globalVariables)
    expr.evaluate[R](ctxWithGlobals, lazyValuesProvider).map { valueWithLazyContext =>
      listeners.foreach(_.expressionEvaluated(node.id, expressionId, expr.original, ctx, metaData, valueWithLazyContext.value))
      ValueWithContext(valueWithLazyContext.value, ctx.withLazyContext(valueWithLazyContext.lazyContext))
    }
  }

  private case class NodeIdExceptionWrapper(nodeId: String, exception: Throwable) extends Exception

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"

  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(servicesDefs: Map[String, ObjectWithMethodDef],
            globalVariables: Map[String, Any],
            lazyEvaluationTimeout: FiniteDuration,
            listeners: Seq[ProcessListener] = Seq(LoggingListener)) = {
    new Interpreter(servicesDefs.mapValuesNow(ServiceInvoker(_)), globalVariables, listeners)
  }

  private class LazyValuesProviderImpl(services: Map[String, ServiceInvoker], ctx: Context)
                                      (implicit ec: ExecutionContext, node: Node) extends LazyValuesProvider {

    override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): IO[(LazyContext, T)] = {
      val paramsMap = params.toMap
      context.get[T](serviceId, paramsMap) match {
        case Some(value) =>
          IO.pure((context, value))
        case None =>
          //TODO: maybe it should be Later here???
          IO.fromFuture(Now(evaluateValue[T](serviceId, paramsMap).map { value =>
            //TODO: exception?
            (context.withEvaluatedValue(serviceId, paramsMap, Left(value)), value)
          }))
      }
    }

    private def evaluateValue[T](serviceId: String, paramsMap: Map[String, Any]): Future[T] = {
      services.get(serviceId) match {
        case None => Future.failed(new IllegalArgumentException(s"Service with id: $serviceId doesn't exist"))
        case Some(service) => service.invoke(paramsMap, NodeContext(ctx.id, node.id, serviceId)).map(_.asInstanceOf[T])
      }
    }
  }

}