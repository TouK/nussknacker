package pl.touk.nussknacker.engine

import cats.MonadError
import cats.effect.IO
import cats.syntax.all._
import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.expression.Expression
import pl.touk.nussknacker.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.nussknacker.engine.compiledgraph.service._
import pl.touk.nussknacker.engine.compiledgraph.variable._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

private class InterpreterInternal[F[_]](listeners: Seq[ProcessListener],
                                expressionEvaluator: ExpressionEvaluator,
                                interpreterShape: InterpreterShape[F]
                               )(implicit metaData: MetaData, executor: ExecutionContext) {

  private implicit val monad: MonadError[F, Throwable] = interpreterShape.monadError

  private val expressionName = "expression"

  def interpret(node: Node, ctx: Context): F[Either[List[InterpretationResult], EspExceptionInfo[_ <: Throwable]]] = {
    monad.handleError[Either[List[InterpretationResult], EspExceptionInfo[_ <: Throwable]]](tryToInterpretNode(node, ctx).map(Left(_))) {
      case NodeIdExceptionWrapper(nodeId, exception) =>
        val exInfo = EspExceptionInfo(Some(nodeId), exception, ctx)
        Right(exInfo)
      case NonFatal(ex) =>
        val exInfo = EspExceptionInfo(None, ex, ctx)
        Right(exInfo)
    }
  }

  private def tryToInterpretNode(node: Node, ctx: Context): F[List[InterpretationResult]] = {
    try {
      monad.handleErrorWith(interpretNode(node, ctx))(err => monad.raiseError(transform(node.id)(err)))
    } catch {
      case NonFatal(ex) => monad.raiseError(transform(node.id)(ex))
    }
  }

  private def transform(nodeId: String)(ex: Throwable): Throwable = ex match {
    case ex: NodeIdExceptionWrapper => ex
    case ex: Throwable => NodeIdExceptionWrapper(nodeId, ex)
  }

  private implicit def nodeToId(implicit node: Node): NodeId = NodeId(node.id)

  private def interpretNode(node: Node, ctx: Context): F[List[InterpretationResult]] = {
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
        invoke(ref, ctx).flatMap {
          case ValueWithContext(_, newCtx) => interpretNext(next, newCtx)
        }
      case Processor(_, _, next, true) => interpretNext(next, ctx)
      case EndingProcessor(id, ref, false) =>
        invoke(ref, ctx).map {
          case ValueWithContext(output, newCtx) =>
            List(InterpretationResult(EndReference(id), output, newCtx))
        }
      case EndingProcessor(id, _, true) =>
        //FIXME: null??
        monad.pure(List(InterpretationResult(EndReference(id), null, ctx)))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap {
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
        monad.pure(List(InterpretationResult(EndReference(id), null, ctx)))
      case Sink(id, ref, optionalExpression, false) =>
        val valueWithModifiedContext = optionalExpression match {
          case Some((expression, _)) =>
            evaluateExpression[Any](expression, ctx, expressionName)
          case None =>
            ValueWithContext(outputValue(ctx), ctx)
        }
        listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
        monad.pure(List(InterpretationResult(EndReference(id), valueWithModifiedContext)))
      case BranchEnd(e) =>
        monad.pure(List(InterpretationResult(e.joinReference, null, ctx)))
      case CustomNode(_, next) =>
        interpretNext(next, ctx)
      case EndingCustomNode(id) =>
        monad.pure(List(InterpretationResult(EndReference(id), null, ctx)))
      case SplitNode(_, nexts) =>
        import cats.implicits._
        nexts.map(interpretNext(_, ctx)).sequence.map(_.flatten)
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context): F[List[InterpretationResult]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        monad.pure(List(InterpretationResult(DeadEndReference(node.id), outputValue(ctx), ctx)))
    }
  }

  private def interpretNext(next: Next, ctx: Context): F[List[InterpretationResult]] =
    next match {
      case NextNode(node) => tryToInterpretNode(node, ctx)
      case PartRef(ref) => monad.pure(List(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx)))
    }

  //hmm... is this OK?
  private def outputValue(ctx: Context): Any =
  ctx.getOrElse[Any](VariableConstants.OutputVariableName, new java.util.HashMap[String, Any]())

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

  private def invoke(ref: ServiceRef, ctx: Context)(implicit node: Node): F[ValueWithContext[Any]] = {
    val (preparedParams, resultFuture) = ref.invoke(ctx, expressionEvaluator)
    resultFuture.onComplete { result =>
      //TODO: what about implicit??
      listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
    }
    interpreterShape.fromFuture(resultFuture.map(ValueWithContext(_, ctx))(SynchronousExecutionContext.ctx))
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context, name: String)
                                   (implicit ec: ExecutionContext, metaData: MetaData, node: Node): ValueWithContext[R] = {
    expressionEvaluator.evaluate(expr, name, node.id, ctx)
  }

  private case class NodeIdExceptionWrapper(nodeId: String, exception: Throwable) extends Exception

}


class Interpreter(listeners: Seq[ProcessListener],
                  expressionEvaluator: ExpressionEvaluator) {

  def interpret[F[_]](node: Node,
                      metaData: MetaData,
                      ctx: Context)
                     (implicit shape: InterpreterShape[F],
                      ec: ExecutionContext): F[Either[List[InterpretationResult], EspExceptionInfo[_ <: Throwable]]] = {
    new InterpreterInternal[F](listeners, expressionEvaluator, shape)(metaData, ec).interpret(node, ctx)
  }

}

object Interpreter {

  def apply(listeners: Seq[ProcessListener],
            expressionEvaluator: ExpressionEvaluator): Interpreter = {
    new Interpreter(listeners, expressionEvaluator)
  }

  //Interpreter can be invoked with various effects, we require MonadError capabilities and ability to convert service invocation results
  trait InterpreterShape[F[_]] {

    def monadError: MonadError[F, Throwable]

    def fromFuture[T]: Future[T] => F[T]

  }

  implicit object IOShape extends InterpreterShape[IO] {
    import IO._

    override def monadError: MonadError[IO, Throwable] = MonadError[IO, Throwable]

    override def fromFuture[T]: Future[T] => IO[T] = f => IO.fromFuture(IO.pure(f))

  }

  class FutureShape(implicit ec: ExecutionContext) extends InterpreterShape[Future] {

    override def monadError: MonadError[Future, Throwable] = cats.instances.future.catsStdInstancesForFuture(ec)

    override def fromFuture[T]: Future[T] => Future[T] = identity
  }

}