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

  def interpret(node: Node,
                metaData: MetaData,
                ctx: Context)
               (implicit executor: ExecutionContext): Future[Either[List[InterpretationResult], EspExceptionInfo[_<:Throwable]]] = {
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
                           (implicit metaData: MetaData, executor: ExecutionContext): Future[List[InterpretationResult]] = {
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
                           (implicit metaData: MetaData, executor: ExecutionContext): Future[List[InterpretationResult]] = {
    implicit val nodeImplicit: Node = node
    listeners.foreach(_.nodeEntered(node.id, ctx, metaData))
    node match {
      case Source(_, next) =>
        interpretNext(next, ctx)
      case VariableBuilder(_, varName, Right(fields), next) =>
        val variable = createOrUpdateVariable(ctx, varName, fields)
        interpretNext(next, variable)
      case VariableBuilder(_, varName, Left(expression), next) =>
        val valueWithModifiedContext = expressionEvaluator.evaluate[Any](expression, varName, node.id, ctx)
        interpretNext(next, ctx.withVariable(varName, valueWithModifiedContext.value))
      case SubprocessStart(_, params, next) =>
        val (newCtx, vars) = expressionEvaluator.evaluateParameters(params, ctx)
        interpretNext(next, newCtx.pushNewContext(vars))
      case SubprocessEnd(_, varName, fields, next) =>
        val updatedCtx = createOrUpdateVariable(ctx, varName, fields)
        val parentContext = ctx.popContext
        val newParentContext = updatedCtx.variables.get(varName).map { value =>
          parentContext.withVariable(varName, value)
        }.getOrElse(parentContext)
        interpretNext(next, newParentContext)
      case Processor(_, ref, next, false) =>
        invoke(ref, None, ctx).flatMap {
          case ValueWithContext(_, newCtx) => interpretNext(next, newCtx)
        }
      case Processor(_, ref, next, true) => interpretNext(next, ctx)
      case EndingProcessor(id, ref, false) =>
        invoke(ref, None, ctx).map {
          case ValueWithContext(output, newCtx) =>
            List(InterpretationResult(EndReference(id), output, newCtx))
        }
      case EndingProcessor(id, _, true) =>
        //FIXME: null??
        Future.successful(List(InterpretationResult(EndReference(id), null, ctx)))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, Some(outName), ctx).flatMap {
          case ValueWithContext(out, newCtx) =>
            interpretNext(next, newCtx.withVariable(outName, out))
        }
      case Filter(_, expression, nextTrue, nextFalse, disabled) =>
        val valueWithModifiedContext = if (disabled) ValueWithContext(true, ctx) else evaluateExpression[Boolean](expression, ctx, expressionName)
        if (disabled || valueWithModifiedContext.value)
          interpretNext(nextTrue, valueWithModifiedContext.context)
        else
          interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
      case Switch(_, expression, exprVal, nexts, defaultNext) =>
        val vmc = evaluateExpression[Any](expression, ctx, expressionName)
        val newCtx = (vmc.context.withVariable(exprVal, vmc.value), Option.empty[Next])
        nexts.zipWithIndex.foldLeft(newCtx) { case (acc, (casee, i)) =>
          acc match {
            case (accCtx, None) =>
              val valueWithModifiedContext = evaluateExpression[Boolean](casee.expression, accCtx, s"$expressionName-$i")
              if (valueWithModifiedContext.value) {
                (valueWithModifiedContext.context, Some(casee.node))
              } else {
                (valueWithModifiedContext.context, None)
              }
            case a => a
          }
        } match {
          case (accCtx, Some(nextNode)) =>
            interpretNext(nextNode, accCtx)
          case (accCtx, None) =>
            interpretOptionalNext(node, defaultNext, accCtx)
        }
      case Sink(id, _, _, true) =>
        Future.successful(List(InterpretationResult(EndReference(id), null, ctx)))
      case Sink(id, ref, optionalExpression, false) =>
        val valueWithModifiedContext = (optionalExpression match {
          case Some((expression, _)) =>
            evaluateExpression[Any](expression, ctx, expressionName)
          case None =>
            ValueWithContext(outputValue(ctx), ctx)
        })
        listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
        Future.successful(List(InterpretationResult(EndReference(id), valueWithModifiedContext)))
      case BranchEnd(e) =>
        Future.successful(List(InterpretationResult(e.joinReference, null, ctx)))
      case CustomNode(_, next) =>
        interpretNext(next, ctx)
      case EndingCustomNode(id) =>
        Future.successful(List(InterpretationResult(EndReference(id), null, ctx)))
      case SplitNode(_, nexts) =>
        Future.sequence(nexts.map(interpretNext(_, ctx))).map(_.flatten)
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context)
                                   (implicit metaData: MetaData, ec: ExecutionContext): Future[List[InterpretationResult]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        Future.successful(List(InterpretationResult(DeadEndReference(node.id), outputValue(ctx), ctx)))
    }
  }

  private def interpretNext(next: Next, ctx: Context)
                           (implicit metaData: MetaData, executor: ExecutionContext): Future[List[InterpretationResult]] =
    next match {
      case NextNode(node) => tryToInterpretNode(node, ctx)
      case PartRef(ref) => Future.successful(List(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx)))
    }

  //hmm... is this OK?
  private def outputValue(ctx: Context): Any =
  ctx.getOrElse[Any](OutputParamName, new java.util.HashMap[String, Any]())

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])
                                    (implicit ec: ExecutionContext, metaData: MetaData, node: Node): Context = {
    val contextWithInitialVariable = ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))

    fields.foldLeft(contextWithInitialVariable) {
      case (context, field) =>
      val valueWithContext = expressionEvaluator.evaluate[Any](field.expression, field.name, node.id, context)
        valueWithContext.context.modifyVariable[java.util.Map[String, Any]](varName, { m =>
          val newMap = new java.util.HashMap[String, Any](m)
          newMap.put(field.name, valueWithContext.value)
          newMap
        })
    }
  }

  private def invoke(ref: ServiceRef, outputVariableNameOpt: Option[String], ctx: Context)
                    (implicit executionContext: ExecutionContext, metaData: MetaData, node: Node): Future[ValueWithContext[Any]] = {
    val (newCtx, preparedParams) = expressionEvaluator.evaluateParameters(ref.parameters, ctx)
    val resultFuture = ref.invoker.invoke(preparedParams, NodeContext(ctx.id, node.id, ref.id, outputVariableNameOpt))
    resultFuture.onComplete { result =>
      //TODO: what about implicit??
      listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
    }
    resultFuture.map(ValueWithContext(_, newCtx))(SynchronousExecutionContext.ctx)
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context, name: String)
                                   (implicit ec: ExecutionContext, metaData: MetaData, node: Node):  ValueWithContext[R] = {
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