package pl.touk.nussknacker.engine

import cats.MonadError
import cats.effect.IO
import cats.syntax.all._
import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.expression.Expression
import pl.touk.nussknacker.engine.api.process.RunMode
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
                                        interpreterShape: InterpreterShape[F],
                                        runMode: RunMode
                                       )(implicit metaData: MetaData, executor: ExecutionContext) {

  private implicit val monad: MonadError[F, Throwable] = interpreterShape.monadError

  private val expressionName = "expression"

  def interpret(node: Node, ctx: Context): F[List[Either[InterpretationResult, EspExceptionInfo[_ <: Throwable]]]] = {
    def flatten[A,B](a: List[Either[List[A], B]]): List[Either[A, B]] = a.flatMap {
      case Left(value) => value.map(Left(_))
      case Right(value) => Right(value) :: Nil
    }

    tryToInterpretNode(node, ctx).map(f => {
      monad.handleError[Either[List[InterpretationResult], EspExceptionInfo[_ <: Throwable]]](f.map(Left(_))) {
        case NodeIdExceptionWrapper(nodeId, exception) =>
          val exInfo = EspExceptionInfo(Some(nodeId), exception, ctx)
          Right(exInfo)
        case NonFatal(ex) =>
          val exInfo = EspExceptionInfo(None, ex, ctx)
          Right(exInfo)
      }
    }).sequence.map(flatten)
  }

  private def tryToInterpretNode(node: Node, ctx: Context): List[F[List[InterpretationResult]]] = {
    interpretNode(node, ctx).map { f =>
      try {
        monad.handleErrorWith(f)(err => monad.raiseError(transform(node.id)(err)))
      } catch {
        case NonFatal(ex) => monad.raiseError(transform(node.id)(ex))
      }
    }.asInstanceOf[List[F[List[InterpretationResult]]]] //todo
  }

  private def transform(nodeId: String)(ex: Throwable): Throwable = ex match {
    case ex: NodeIdExceptionWrapper => ex
    case ex: Throwable => NodeIdExceptionWrapper(nodeId, ex)
  }

  private implicit def nodeToId(implicit node: Node): NodeId = NodeId(node.id)

  private def interpretNode(node: Node, ctx: Context): List[F[List[InterpretationResult]]] = {
    implicit val nodeImplicit: Node = node
    node match {
      // We do not invoke listener 'nodeEntered' here for nodes which are wrapped in PartRef by ProcessSplitter.
      // These are handled in interpretNext method
      case CustomNode(_,_) | EndingCustomNode(_) | Sink(_, _, _) =>
      case _ => listeners.foreach(_.nodeEntered(node.id, ctx, metaData))
    }
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
          case ValueWithContext(_, newCtx) => interpretNext(next, newCtx).head
        }  :: Nil
      case Processor(_, _, next, true) => interpretNext(next, ctx)
      case EndingProcessor(id, ref, false) =>
        invoke(ref, ctx).map {
          case ValueWithContext(output, newCtx) =>
            List(InterpretationResult(EndReference(id), output, newCtx))
        } :: Nil
      case EndingProcessor(id, _, true) =>
        //FIXME: null??
        monad.pure(List(InterpretationResult(EndReference(id), null, ctx))) :: Nil
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap {
          case ValueWithContext(out, newCtx) =>
            interpretNext(next, newCtx.withVariable(outName, out)).head
        } :: Nil
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
      case Sink(id, _, true) =>
        monad.pure(List(InterpretationResult(EndReference(id), null, ctx))) :: Nil
      case Sink(id, ref, false) =>
        val valueWithModifiedContext = ValueWithContext(null, ctx)
        listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
        monad.pure(List(InterpretationResult(EndReference(id), valueWithModifiedContext))) :: Nil
      case BranchEnd(e) =>
        monad.pure(List(InterpretationResult(e.joinReference, null, ctx))) :: Nil
      case CustomNode(_, next) =>
        interpretNext(next, ctx)
      case EndingCustomNode(id) =>
        monad.pure(List(InterpretationResult(EndReference(id), null, ctx))) :: Nil
      case SplitNode(_, nexts) =>
        nexts.flatMap(interpretNext(_, ctx))
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context): List[F[List[InterpretationResult]]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        monad.pure(List(InterpretationResult(DeadEndReference(node.id), null, ctx))) :: Nil
    }
  }

  private def interpretNext(next: Next, ctx: Context): List[F[List[InterpretationResult]]] = next match {
    case NextNode(node) =>
      tryToInterpretNode(node, ctx)
    case pr@PartRef(ref) => {
      listeners.foreach(_.nodeEntered(pr.id, ctx, metaData))
      monad.pure(List(InterpretationResult(NextPartReference(ref), null, ctx))) :: Nil
    }
  }

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
    implicit val implicitRunMode: RunMode = runMode
    val (preparedParams, resultFuture) = ref.invoke(ctx, expressionEvaluator)
    resultFuture.onComplete { result =>
      //TODO: what about implicit??
      listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
    }
    interpreterShape.fromFuture(SynchronousExecutionContext.ctx)(resultFuture.map(ValueWithContext(_, ctx))(SynchronousExecutionContext.ctx))
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context, name: String)
                                   (implicit ec: ExecutionContext, metaData: MetaData, node: Node): ValueWithContext[R] = {
    expressionEvaluator.evaluate(expr, name, node.id, ctx)
  }

  private case class NodeIdExceptionWrapper(nodeId: String, exception: Throwable) extends Exception

}


class Interpreter(listeners: Seq[ProcessListener],
                  expressionEvaluator: ExpressionEvaluator,
                  runMode: RunMode) {

  def interpret[F[_]](node: Node,
                      metaData: MetaData,
                      ctx: Context)
                     (implicit shape: InterpreterShape[F],
                      ec: ExecutionContext): F[List[Either[InterpretationResult, EspExceptionInfo[_ <: Throwable]]]] = {
    new InterpreterInternal[F](listeners, expressionEvaluator, shape, runMode)(metaData, ec).interpret(node, ctx)
  }

}

object Interpreter {

  def apply(listeners: Seq[ProcessListener],
            expressionEvaluator: ExpressionEvaluator,
            runMode: RunMode): Interpreter = {
    new Interpreter(listeners, expressionEvaluator, runMode)
  }

  //Interpreter can be invoked with various effects, we require MonadError capabilities and ability to convert service invocation results
  trait InterpreterShape[F[_]] {

    def monadError: MonadError[F, Throwable]

    def fromFuture[T](implicit ec: ExecutionContext): Future[T] => F[T]

  }

  implicit object IOShape extends InterpreterShape[IO] {
    import IO._

    override def monadError: MonadError[IO, Throwable] = MonadError[IO, Throwable]

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => IO[T] = f => IO.fromFuture(IO.pure(f))(IO.contextShift(ec))

  }

  class FutureShape(implicit ec: ExecutionContext) extends InterpreterShape[Future] {

    override def monadError: MonadError[Future, Throwable] = cats.instances.future.catsStdInstancesForFuture(ec)

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => Future[T] = identity
  }

}
