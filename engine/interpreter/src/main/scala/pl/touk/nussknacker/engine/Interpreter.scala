package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compiledgraph.expression._
import pl.touk.nussknacker.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.nussknacker.engine.compiledgraph.service._
import pl.touk.nussknacker.engine.compiledgraph.variable._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class Interpreter private(listeners: Seq[ProcessListener], expressionEvaluator: ExpressionEvaluator) {

  def interpret(node: Node,
                metaData: MetaData,
                ctx: Context)
               (implicit executor: ExecutionContext): Future[Either[InterpretationResult, EspExceptionInfo[_<:Throwable]]] = {
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
                           (implicit metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] = {
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

  private implicit def nodeToId(implicit node: Node) : NodeId = NodeId(node.id)

  private def interpretNode(node: Node, ctx: Context)
                           (implicit metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] = {
    implicit val nodeImplicit = node
    listeners.foreach(_.nodeEntered(node.id, ctx, metaData))
    node match {
      case Source(_, next) =>
        interpretNext(next, ctx)
      case VariableBuilder(_, varName, Right(fields), next) =>
        createOrUpdateVariable(ctx, varName, fields).flatMap(interpretNext(next, _))
      case VariableBuilder(_, varName, Left(expression), next) =>
        expressionEvaluator.evaluate[Any](expression, varName, node.id, ctx).flatMap { valueWithModifiedContext =>
          interpretNext(next, ctx.withVariable(varName, valueWithModifiedContext.value))
        }
      case SubprocessStart(_, params, next) =>
        expressionEvaluator.evaluateParameters(params, ctx).flatMap { case (newCtx, vars) =>
          interpretNext(next, newCtx.pushNewContext(vars))
        }
      case SubprocessEnd(id, next) =>
        interpretNext(next, ctx.popContext)
      case Processor(_, ref, next, false) =>
        invoke(ref, ctx).flatMap {
          case ValueWithContext(_, newCtx) => interpretNext(next, newCtx)
        }
      case Processor(_, ref, next, true) => interpretNext(next, ctx)
      case EndingProcessor(id, ref, false) =>
        invoke(ref, ctx).map {
          case ValueWithContext(output, newCtx) =>
            InterpretationResult(EndReference(id), output, newCtx)
        }
      case EndingProcessor(id, ref, true) =>
        //FIXME: null??
        Future.successful(InterpretationResult(EndReference(id), null, ctx))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap {
          case ValueWithContext(out, newCtx) =>
            interpretNext(next, newCtx.withVariable(outName, out))
        }
      case Filter(_, expression, nextTrue, nextFalse, disabled) =>
        evaluateExpression[Boolean](expression, ctx).flatMap { valueWithModifiedContext =>
          if (disabled || valueWithModifiedContext.value)
            interpretNext(nextTrue, valueWithModifiedContext.context)
          else
            interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
        }
      case Switch(_, expression, exprVal, nexts, defaultNext) =>
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
      case Sink(id, ref, optionalExpression) =>
        (optionalExpression match {
          case Some(expression) =>
            evaluateExpression[Any](expression, ctx)
          case None =>
            Future.successful(ValueWithContext(outputValue(ctx), ctx))
        }).map { valueWithModifiedContext =>
          listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
          InterpretationResult(EndReference(id), valueWithModifiedContext)
        }
      case cust: CustomNode =>
        interpretNext(cust.next, ctx)
      //FIXME: can this ever happen?
      case cust: SplitNode =>
        throw new IllegalArgumentException(s"Split node encountered, should not happen: $cust")
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context)
                                   (implicit metaData: MetaData, ec: ExecutionContext): Future[InterpretationResult] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        Future.successful(InterpretationResult(DeadEndReference(node.id), outputValue(ctx), ctx))
    }
  }

  private def interpretNext(next: Next, ctx: Context)
                           (implicit metaData: MetaData, executor: ExecutionContext): Future[InterpretationResult] =
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
        context.flatMap(expressionEvaluator.evaluate[Any](field.expression, field.name, node.id, _)).map { valueWithContext =>
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
    expressionEvaluator.evaluateParameters(ref.parameters, ctx).flatMap { case (newCtx, preparedParams) =>
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
  = expressionEvaluator.evaluate(expr, "expression", node.id, ctx)

  private case class NodeIdExceptionWrapper(nodeId: String, exception: Throwable) extends Exception

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"


  def apply(listeners: Seq[ProcessListener],
            expressionEvaluator: ExpressionEvaluator) = {
    new Interpreter(listeners, expressionEvaluator)
  }

}