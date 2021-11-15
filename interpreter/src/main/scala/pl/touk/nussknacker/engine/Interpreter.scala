package pl.touk.nussknacker.engine

import cats.Monad
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
import scala.util.{Failure, Success}

private class InterpreterInternal[F[_]](listeners: Seq[ProcessListener],
                                        expressionEvaluator: ExpressionEvaluator,
                                        interpreterShape: InterpreterShape[F],
                                        runMode: RunMode
                                       )(implicit metaData: MetaData, executor: ExecutionContext) {


  type Result[T] = Either[T, EspExceptionInfo[_ <: Throwable]]

  private implicit val monad: Monad[F] = interpreterShape.monad

  private val expressionName = "expression"

  def interpret(node: Node, ctx: Context): F[List[Result[InterpretationResult]]] = {
    try {
      interpretNode(node, ctx)
    } catch {
      case NonFatal(ex) =>
        monad.pure(List(Right(handleError(node, ctx)(ex))))
    }
  }

  private implicit def nodeToId(implicit node: Node): NodeId = NodeId(node.id)

  private def handleError(node: Node, ctx: Context): Throwable => EspExceptionInfo[_ <: Throwable] = EspExceptionInfo(Some(node.id), _, ctx)

  private def interpretNode(node: Node, ctx: Context): F[List[Result[InterpretationResult]]] = {
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
          case Left(ValueWithContext(_, newCtx)) => interpretNext(next, newCtx)
          case Right(exInfo) => monad.pure(List(Right(exInfo)))
        }
      case Processor(_, _, next, true) => interpretNext(next, ctx)
      case EndingProcessor(id, ref, false) =>
        invoke(ref, ctx).map {
          case Left(ValueWithContext(output, newCtx)) =>
            List(Left(InterpretationResult(EndReference(id), output, newCtx)))
          case r@Right(exInfo) => List(Right(exInfo))
        }
      case EndingProcessor(id, _, true) =>
        //FIXME: null??
        monad.pure(List(Left(InterpretationResult(EndReference(id), null, ctx))))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap {
          case Left(ValueWithContext(out, newCtx)) => interpretNext(next, newCtx.withVariable(outName, out))
          case Right(exInfo) => monad.pure(List(Right(exInfo)))
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
      case Sink(id, _, true) =>
        monad.pure(List(Left(InterpretationResult(EndReference(id), null, ctx))))
      case Sink(id, ref, false) =>
        val valueWithModifiedContext = ValueWithContext(null, ctx)
        listeners.foreach(_.sinkInvoked(node.id, ref, ctx, metaData, valueWithModifiedContext.value))
        monad.pure(List(Left(InterpretationResult(EndReference(id), valueWithModifiedContext))))
      case BranchEnd(e) =>
        monad.pure(List(Left(InterpretationResult(e.joinReference, null, ctx))))
      case CustomNode(_, next) =>
        interpretNext(next, ctx)
      case EndingCustomNode(id) =>
        monad.pure(List(Left(InterpretationResult(EndReference(id), null, ctx))))
      case SplitNode(_, nexts) =>
        import cats.implicits._
        nexts.map(interpretNext(_, ctx)).sequence.map(_.flatten)
    }
  }

  private def interpretOptionalNext(node: Node, optionalNext: Option[Next], ctx: Context): F[List[Result[InterpretationResult]]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        monad.pure(List(Left(InterpretationResult(DeadEndReference(node.id), null, ctx))))
    }
  }

  private def interpretNext(next: Next, ctx: Context): F[List[Result[InterpretationResult]]] = next match {
    case NextNode(node) => interpret(node, ctx)
    case pr@PartRef(ref) => {
      listeners.foreach(_.nodeEntered(pr.id, ctx, metaData))
      monad.pure(List(Left(InterpretationResult(NextPartReference(ref), null, ctx))))
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

  private def invoke(ref: ServiceRef, ctx: Context)(implicit node: Node): F[Result[ValueWithContext[Any]]] = {
    implicit val implicitRunMode: RunMode = runMode
    val (preparedParams, resultFuture) = ref.invoke(ctx, expressionEvaluator)
    resultFuture.onComplete { result =>
      //TODO: what about implicit??
      listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, preparedParams, result))
    }
    val syncEc = SynchronousExecutionContext.ctx
    interpreterShape.fromFuture(syncEc)(resultFuture.map(ValueWithContext(_, ctx))(syncEc)).map {
      case Right(ex) => Right(handleError(node, ctx)(ex))
      case Left(value) => Left(value)
    }
  }

  private def evaluateExpression[R](expr: Expression, ctx: Context, name: String)
                                   (implicit ec: ExecutionContext, metaData: MetaData, node: Node): ValueWithContext[R] = {
    expressionEvaluator.evaluate(expr, name, node.id, ctx)
  }
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

  object InterpreterShape {
    def transform[T](f: Future[T])(implicit ec: ExecutionContext): Future[Either[T, Throwable]] = f.transformWith {
      case Failure(exception) => Future.successful(Right(exception))
      case Success(value) => Future.successful(Left(value))
    }
  }

  //Interpreter can be invoked with various effects, we require MonadError capabilities and ability to convert service invocation results
  trait InterpreterShape[F[_]] {

    def monad: Monad[F]

    def fromFuture[T](implicit ec: ExecutionContext): Future[T] => F[Either[T, Throwable]]

  }
  import InterpreterShape._

  implicit object IOShape extends InterpreterShape[IO] {

    import IO._

    override def monad: Monad[IO] = Monad[IO]

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => IO[Either[T, Throwable]] =
      f => IO.fromFuture(IO.pure(transform(f)))(IO.contextShift(ec))
  }

  class FutureShape(implicit ec: ExecutionContext) extends InterpreterShape[Future] {

    override def monad: Monad[Future] = cats.instances.future.catsStdInstancesForFuture(ec)

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => Future[Either[T, Throwable]] = transform(_)
  }

}