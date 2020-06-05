package pl.touk.nussknacker.engine

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.expression.Expression
import pl.touk.nussknacker.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.nussknacker.engine.compiledgraph.service._
import pl.touk.nussknacker.engine.compiledgraph.variable._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class Interpreter private(listeners: Seq[ProcessListener], expressionEvaluator: ExpressionEvaluator) {

  private val expressionName = "expression"

  private implicit val syncEc: ExecutionContext = SynchronousExecutionContext.ctx

  def interpret(node: Node,
                metaData: MetaData,
                ctx: Context)
               (implicit executor: ExecutionContext): Future[Either[List[InterpretationResult], EspExceptionInfo[_<:Throwable]]] = {
    implicit val impMetaData: MetaData = metaData
    tryToInterpretNode(node, ctx, executor).map(Left(_))(syncEc).recover {
      case ex@NodeIdExceptionWrapper(nodeId, exception) =>
        val exInfo = EspExceptionInfo(Some(nodeId), exception, ctx)
        Right(exInfo)
      case NonFatal(ex) =>
        val exInfo = EspExceptionInfo(None, ex, ctx)
        Right(exInfo)
    }(syncEc)
  }

  private def tryToInterpretNode(node: Node, ctx: Context, serviceEc: ExecutionContext)
                           (implicit metaData: MetaData): Future[List[InterpretationResult]] = {
    try {
      interpretNode(node, ctx, serviceEc).transform(identity, transform(node.id))
    } catch {
      case NonFatal(ex) => Future.failed(transform(node.id)(ex))
    }
  }

  private def transform(nodeId: String)(ex: Throwable) : Throwable = ex match {
    case ex: NodeIdExceptionWrapper => ex
    case ex: Throwable => NodeIdExceptionWrapper(nodeId, ex)
  }

  private implicit def nodeToId(implicit node: Node) : NodeId = NodeId(node.id)

  private def interpretNode(node: Node, ctx: Context, serviceEc: ExecutionContext)
                           (implicit metaData: MetaData): Future[List[InterpretationResult]] = {
    implicit val nodeImplicit = node
    listeners.foreach(_.nodeEntered(node.id, ctx, metaData))
    node match {
      case Source(_, next) =>
        interpretNext(next, ctx, serviceEc)
      case VariableBuilder(_, varName, Right(fields), next) =>
        createOrUpdateVariable(ctx, varName, fields).flatMap(interpretNext(next, _, serviceEc))
      case VariableBuilder(_, varName, Left(expression), next) =>
        expressionEvaluator.evaluate[Any](expression, varName, node.id, ctx).flatMap { valueWithModifiedContext =>
          interpretNext(next, ctx.withVariable(varName, valueWithModifiedContext.value), serviceEc)
        }
      case SubprocessStart(_, params, next) =>
        expressionEvaluator.evaluateParameters(params, ctx).flatMap { case (newCtx, vars) =>
          interpretNext(next, newCtx.pushNewContext(vars), serviceEc)
        }
      case SubprocessEnd(_, varName, fields, next) =>
        createOrUpdateVariable(ctx, varName, fields).flatMap { updatedCtx =>
          val parentContext = ctx.popContext
          val newParentContext = updatedCtx.variables.get(varName).map { value =>
            parentContext.withVariable(varName, value)
          }.getOrElse(parentContext)
          interpretNext(next, newParentContext, serviceEc)
        }
      case Processor(_, ref, next, false) =>
        invoke(ref, None, ctx, serviceEc).flatMap {
          case ValueWithContext(_, newCtx) => interpretNext(next, newCtx, serviceEc)
        }
      case Processor(_, ref, next, true) => interpretNext(next, ctx, serviceEc)
      case EndingProcessor(id, ref, false) =>
        invoke(ref, None, ctx, serviceEc).map {
          case ValueWithContext(output, newCtx) =>
            List(InterpretationResult(EndReference(id), output, newCtx))
        }
      case EndingProcessor(id, _, true) =>
        //FIXME: null??
        Future.successful(List(InterpretationResult(EndReference(id), null, ctx)))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, Some(outName), ctx, serviceEc).flatMap {
          case ValueWithContext(out, newCtx) =>
            interpretNext(next, newCtx.withVariable(outName, out), serviceEc)
        }
      case Filter(_, expression, nextTrue, nextFalse, disabled) =>
        val expressionResult = if (disabled) Future.successful(ValueWithContext(true, ctx)) else evaluateExpression[Boolean](expression, ctx, expressionName)
        expressionResult.flatMap { valueWithModifiedContext =>
          if (disabled || valueWithModifiedContext.value)
            interpretNext(nextTrue, valueWithModifiedContext.context, serviceEc)
          else
            interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context, serviceEc)
        }
      case Switch(_, expression, exprVal, nexts, defaultNext) =>
        val valueWithModifiedContext = evaluateExpression[Any](expression, ctx, expressionName)
        val newCtx = valueWithModifiedContext.map( vmc =>
          (vmc.context.withVariable(exprVal, vmc.value), Option.empty[Next]))
        nexts.zipWithIndex.foldLeft(newCtx) { case (acc, (casee, i)) =>
          acc.flatMap {
            case (accCtx, None) => evaluateExpression[Boolean](casee.expression, accCtx, s"$expressionName-$i").map { valueWithModifiedContext =>
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
            interpretNext(nextNode, accCtx, serviceEc)
          case (accCtx, None) =>
            interpretOptionalNext(node, defaultNext, accCtx, serviceEc)
        }
      case Sink(id, _, _, true) =>
        Future.successful(List(InterpretationResult(EndReference(id), null, ctx)))
      case Sink(id, ref, optionalExpression, false) =>
        (optionalExpression match {
          case Some((expression, _)) =>
            evaluateExpression[Any](expression, ctx, expressionName)
          case None =>
            Future.successful(ValueWithContext(outputValue(ctx), ctx))
        }).map { valueWithModifiedContext =>
          listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
          List(InterpretationResult(EndReference(id), valueWithModifiedContext))
        }
      case BranchEnd(e) =>
        Future.successful(List(InterpretationResult(e.joinReference, null, ctx)))
      case CustomNode(_, next) =>
        interpretNext(next, ctx, serviceEc)
      case EndingCustomNode(id) =>
        Future.successful(List(InterpretationResult(EndReference(id), null, ctx)))
      case SplitNode(id, nexts) =>
        Future.sequence(nexts.map(interpretNext(_, ctx, serviceEc))).map(_.flatten)
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context, serviceEc: ExecutionContext)
                                   (implicit metaData: MetaData): Future[List[InterpretationResult]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx, serviceEc)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        Future.successful(List(InterpretationResult(DeadEndReference(node.id), outputValue(ctx), ctx)))
    }
  }

  private def interpretNext(next: Next, ctx: Context, serviceEc: ExecutionContext)
                           (implicit metaData: MetaData): Future[List[InterpretationResult]] =
    next match {
      case NextNode(node) => tryToInterpretNode(node, ctx, serviceEc)
      case PartRef(ref) => Future.successful(List(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx)))
    }

  //hmm... is this OK?
  private def outputValue(ctx: Context): Any =
  ctx.getOrElse[Any](OutputParamName, new java.util.HashMap[String, Any]())

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])
                                    (implicit metaData: MetaData, node: Node): Future[Context] = {
    val contextWithInitialVariable = ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))

    fields.foldLeft(Future.successful(contextWithInitialVariable)) {
      case (context, field) =>
        context.flatMap(expressionEvaluator.evaluate[Any](field.expression, field.name, node.id, _)).map { valueWithContext =>
          valueWithContext.context.modifyVariable[java.util.Map[String, Any]](varName, { m =>
            val newMap = new java.util.HashMap[String, Any](m)
            newMap.put(field.name, valueWithContext.value)
            newMap
          })
        }
    }
  }

  private def invoke(ref: ServiceRef, outputVariableNameOpt: Option[String], ctx: Context, serviceEc: ExecutionContext)
                    (implicit metaData: MetaData, node: Node): Future[ValueWithContext[Any]] = {
    expressionEvaluator.evaluateParameters(ref.parameters, ctx).flatMap { case (newCtx, preparedParams) =>
      val resultFuture = ref.invoker.invoke(preparedParams, NodeContext(ctx.id, node.id, ref.id, outputVariableNameOpt))(serviceEc, metaData)
      resultFuture.onComplete { result =>
        //TODO: what about implicit??
        listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
      }
      resultFuture.map(ValueWithContext(_, newCtx))(SynchronousExecutionContext.ctx)
    }
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context, name: String)
                                   (implicit metaData: MetaData, node: Node):  Future[ValueWithContext[R]] = {
    expressionEvaluator.evaluate(expr, name, node.id, ctx)
  }

  private case class NodeIdExceptionWrapper(nodeId: String, exception: Throwable) extends Exception

}

object Interpreter {

  final val InputParamName = "input"
  final val MetaParamName = "meta"
  final val OutputParamName = "output"


  def apply(listeners: Seq[ProcessListener],
            expressionEvaluator: ExpressionEvaluator) = {
    new Interpreter(listeners, expressionEvaluator)
  }

}