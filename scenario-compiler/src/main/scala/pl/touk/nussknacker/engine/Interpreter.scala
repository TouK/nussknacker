package pl.touk.nussknacker.engine

import cats.Monad
import cats.effect.IO
import cats.syntax.all._
import com.github.ghik.silencer.silent
import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ServiceExecutionContext}
import pl.touk.nussknacker.engine.compiledgraph.node._
import pl.touk.nussknacker.engine.compiledgraph.service._
import pl.touk.nussknacker.engine.compiledgraph.variable._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.node.NodeComponentInfoExtractor
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import java.util
import scala.collection.mutable
import scala.compat.java8.FunctionConverters.enrichAsJavaFunction
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

// TODO Interpreter and things around for sake of clarity of responsibilities between modules should be moved to the separate
//      module used only on runtime side. In the scenario-compiler module would stay only things responsible for
//      for compilation of graph into the runtime logic of components referenced by nodes
private class InterpreterInternal[F[_]: Monad](
    listeners: Seq[ProcessListener],
    expressionEvaluator: ExpressionEvaluator,
    interpreterShape: InterpreterShape[F],
    componentUseCase: ComponentUseCase,
    serviceExecutionContext: ServiceExecutionContext
)(implicit metaData: MetaData) {

  type Result[T] = Either[T, NuExceptionInfo[_ <: Throwable]]

  private val expressionName = "expression"

  def interpret(node: Node, ctx: Context): F[List[Result[InterpretationResult]]] = {
    try {
      interpretNode(node, ctx)
    } catch {
      case NonFatal(ex) =>
        Monad[F].pure(List(Right(handleError(node, ctx)(ex))))
    }
  }

  private implicit def nodeToId(implicit node: Node): NodeId = NodeId(node.id)

  private def handleError(node: Node, ctx: Context): Throwable => NuExceptionInfo[_ <: Throwable] = {
    NuExceptionInfo(Some(NodeComponentInfoExtractor.fromCompiledNode(node)), _, ctx)
  }

  @silent("deprecated")
  private def interpretNode(node: Node, ctx: Context): F[List[Result[InterpretationResult]]] = {
    implicit val nodeImplicit: Node = node
    node match {
      // We do not invoke listener 'nodeEntered' here for nodes which are wrapped in PartRef by ProcessSplitter.
      // These are handled in interpretNext method
      case CustomNode(_, _, _) | EndingCustomNode(_, _) | Sink(_, _, _) | FragmentOutput(_, _, _) =>
      case _ => listeners.foreach(_.nodeEntered(node.id, ctx, metaData))
    }
    node match {
      case Source(_, _, next) =>
        interpretNext(next, ctx)
      case VariableBuilder(_, varName, Right(fields), next) =>
        val variable = createOrUpdateVariable(ctx, varName, fields)
        interpretNext(next, variable)
      case VariableBuilder(_, varName, Left(expression), next) =>
        val valueWithModifiedContext = expressionEvaluator.evaluate[Any](expression, varName, node.id, ctx)
        interpretNext(next, ctx.withVariable(varName, valueWithModifiedContext.value))
      case FragmentUsageStart(_, params, next) =>
        val (newCtx, vars) = expressionEvaluator.evaluateParameters(params, ctx)
        interpretNext(next, newCtx.pushNewContext(vars.map { case (paramName, value) => (paramName.value, value) }))
      case FragmentUsageEnd(_, outputVar, next) =>
        // Here we need parent context so we can compile rest of scenario. Unfortunately some component inside fragment
        // could've cleared that context. In that case, we take current (fragment's) context so we can keep the id,
        // clear it's variables, and keep using it in further processing.
        val parentContext = ctx.parentContext.getOrElse(ctx.copy(variables = Map.empty))
        val newParentContext = outputVar match {
          case Some(FragmentOutputVarDefinition(varName, fields)) =>
            // TODO simplify - only push Map(field -> value) into parentContext instead of the whole object
            val parsedFieldsMap = parseFragmentOutput(ctx, fields)
            parentContext.withVariable(varName, parsedFieldsMap)
          case None => parentContext
        }
        interpretNext(next, newParentContext)
      case Processor(_, ref, next, false) =>
        invokeWrappedInInterpreterShape(ref, ctx).flatMap {
          // for Processor the result is null/BoxedUnit/Void etc. so we ignore it
          case Left(ValueWithContext(_, newCtx)) => interpretNext(next, newCtx)
          case Right(exInfo)                     => Monad[F].pure(List(Right(exInfo)))
        }
      case Processor(_, _, next, true) => interpretNext(next, ctx)
      case EndingProcessor(id, ref, false) =>
        listeners.foreach(_.endEncountered(id, ref.id, ctx, metaData))
        invokeWrappedInInterpreterShape(ref, ctx).map {
          // for Processor the result is null/BoxedUnit/Void etc. so we ignore it
          case Left(ValueWithContext(_, newCtx)) =>
            List(Left(InterpretationResult(EndReference(id), newCtx)))
          case Right(exInfo) => List(Right(exInfo))
        }
      case EndingProcessor(id, _, true) =>
        Monad[F].pure(List(Left(InterpretationResult(EndReference(id), ctx))))
      case FragmentOutput(id, _, true) =>
        Monad[F].pure(List(Left(InterpretationResult(FragmentEndReference(id, Map.empty), ctx))))
      case FragmentOutput(id, fieldsWithExpression, false) =>
        fieldsWithExpression.toList
          .traverse(a =>
            Either
              .catchNonFatal(a._1 -> expressionEvaluator.evaluate(a._2.expression, a._1, id, ctx).value)
              .toValidatedNel
          )
          .map(_.toMap)
          .fold(
            exceptions => {
              listeners.foreach(_.nodeEntered(node.id, ctx, metaData))
              Monad[F].pure(exceptions.toList.map(exc => Right(handleError(node, ctx)(exc))))
            },
            fields => {
              val newCtx = ctx.withVariables(fields)
              listeners.foreach(_.nodeEntered(node.id, newCtx, metaData))
              Monad[F].pure(List(Left(InterpretationResult(FragmentEndReference(id, fields), newCtx))))
            }
          )
      case Enricher(_, ref, outName, next) =>
        invokeWrappedInInterpreterShape(ref, ctx).flatMap {
          case Left(ValueWithContext(out, newCtx)) => interpretNext(next, newCtx.withVariable(outName, out))
          case Right(exInfo)                       => Monad[F].pure(List(Right(exInfo)))
        }
      case Filter(_, expression, nextTrue, nextFalse, disabled) =>
        val valueWithModifiedContext =
          if (disabled) ValueWithContext(true, ctx) else evaluateExpression[Boolean](expression, ctx, expressionName)
        if (disabled || valueWithModifiedContext.value)
          interpretOptionalNext(node, nextTrue, valueWithModifiedContext.context)
        else
          interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
      case Switch(_, expr, nexts, defaultNext) =>
        val newCtx = expr
          .map { case (exprVal, expression) =>
            val vmc = evaluateExpression[Any](expression, ctx, expressionName)
            vmc.context.withVariable(exprVal, vmc.value)
          }
          .getOrElse(ctx)

        nexts.zipWithIndex.foldLeft((newCtx, Option.empty[Next])) { case (acc, (casee, i)) =>
          acc match {
            case (accCtx, None) =>
              val valueWithModifiedContext =
                evaluateExpression[Boolean](casee.expression, accCtx, s"$expressionName-$i")
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
        Monad[F].pure(List(Left(InterpretationResult(EndReference(id), ctx))))
      case Sink(id, ref, false) =>
        listeners.foreach(_.endEncountered(id, ref, ctx, metaData))
        Monad[F].pure(List(Left(InterpretationResult(EndReference(id), ctx))))
      case BranchEnd(e) =>
        Monad[F].pure(List(Left(InterpretationResult(e.joinReference, ctx))))
      case CustomNode(_, _, next) =>
        interpretNext(next, ctx)
      case EndingCustomNode(id, ref) =>
        listeners.foreach(_.endEncountered(id, ref, ctx, metaData))
        Monad[F].pure(List(Left(InterpretationResult(EndReference(id), ctx))))
      case SplitNode(_, nexts) =>
        import cats.implicits._
        nexts.map(interpretNext(_, ctx)).sequence.map(_.flatten)
    }
  }

  private def interpretOptionalNext(
      node: Node,
      optionalNext: Option[Next],
      ctx: Context
  ): F[List[Result[InterpretationResult]]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(next, ctx)
      case None =>
        listeners.foreach(_.deadEndEncountered(node.id, ctx, metaData))
        Monad[F].pure(List(Left(InterpretationResult(DeadEndReference(node.id), ctx))))
    }
  }

  private def interpretNext(next: Next, ctx: Context): F[List[Result[InterpretationResult]]] =
    next match {
      case NextNode(node) => interpret(node, ctx)
      case pr @ PartRef(ref) => {
        listeners.foreach(_.nodeEntered(pr.id, ctx, metaData))
        Monad[F].pure(List(Left(InterpretationResult(NextPartReference(ref), ctx))))
      }
    }

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])(
      implicit metaData: MetaData,
      node: Node
  ): Context = {
    val contextWithInitialVariable =
      ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))

    fields.foldLeft(contextWithInitialVariable) { case (context, field) =>
      val valueWithContext = expressionEvaluator.evaluate[Any](field.expression, field.name, node.id, context)
      valueWithContext.context.modifyVariable[java.util.Map[String, Any]](
        varName,
        { m =>
          val newMap = new java.util.HashMap[String, Any](m)
          newMap.put(field.name, valueWithContext.value)
          newMap
        }
      )
    }
  }

  private def parseFragmentOutput(ctx: Context, fields: Seq[Field])(
      implicit metaData: MetaData,
      node: Node
  ): java.util.HashMap[String, Any] = {
    val fieldsMap = fields
      .map(field => (field.name, expressionEvaluator.evaluate[Any](field.expression, field.name, node.id, ctx).value))
      .toMap

    import scala.jdk.CollectionConverters._
    import scala.collection.mutable
    new java.util.HashMap[String, Any](mutable.Map(fieldsMap.toSeq: _*).asJava)

  }

  private def invokeWrappedInInterpreterShape(ref: ServiceRef, ctx: Context)(
      implicit node: Node
  ): F[Result[ValueWithContext[Any]]] = {
    interpreterShape.fromFuture
      .apply(invoke(ref, ctx)(node))
      .map {
        case Right(ex)   => Right(handleError(node, ctx)(ex))
        case Left(value) => Left(value)
      }
  }

  private def invoke(ref: ServiceRef, ctx: Context)(implicit node: Node) = {
    implicit val implicitComponentUseCase: ComponentUseCase = componentUseCase
    val resultFuture                                        = ref.invoke(ctx, serviceExecutionContext)
    import SynchronousExecutionContextAndIORuntime.syncEc
    resultFuture.onComplete { result =>
      listeners.foreach(_.serviceInvoked(node.id, ref.id, ctx, metaData, result))
    }
    resultFuture.map(ValueWithContext(_, ctx))
  }

  private def evaluateExpression[R](expr: CompiledExpression, ctx: Context, name: String)(
      implicit metaData: MetaData,
      node: Node
  ): ValueWithContext[R] = {
    expressionEvaluator.evaluate(expr, name, node.id, ctx)
  }

}

class Interpreter(
    listeners: Seq[ProcessListener],
    expressionEvaluator: ExpressionEvaluator,
    componentUseCase: ComponentUseCase
) {

  def interpret[F[_]](
      node: Node,
      metaData: MetaData,
      ctx: Context,
      serviceExecutionContext: ServiceExecutionContext
  )(
      implicit monad: Monad[F],
      shape: InterpreterShape[F],
  ): F[List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]]] = {
    implicit val metaDataImplicit: MetaData = metaData
    new InterpreterInternal[F](listeners, expressionEvaluator, shape, componentUseCase, serviceExecutionContext)
      .interpret(node, ctx)
  }

}

object Interpreter {

  def apply(
      listeners: Seq[ProcessListener],
      expressionEvaluator: ExpressionEvaluator,
      componentUseCase: ComponentUseCase
  ): Interpreter = {
    new Interpreter(listeners, expressionEvaluator, componentUseCase)
  }

  object InterpreterShape {

    def transform[T](f: Future[T])(implicit ec: ExecutionContext): Future[Either[T, Throwable]] = f.transformWith {
      case Failure(exception) => Future.successful(Right(exception))
      case Success(value)     => Future.successful(Left(value))
    }

  }

  // Interpreter can be invoked with various effects, we require MonadError capabilities and ability to convert service invocation results
  trait InterpreterShape[F[_]] {

    def fromFuture[T]: Future[T] => F[Either[T, Throwable]]
  }

  import InterpreterShape._

  implicit object IOShape extends InterpreterShape[IO] {

    override def fromFuture[T]: Future[T] => IO[Either[T, Throwable]] = {
      implicit val ctx: ExecutionContext = SynchronousExecutionContextAndIORuntime.syncEc
      f => IO.fromFuture(IO.pure(transform(f)))
    }

  }

  implicit object FutureShape extends InterpreterShape[Future] {

    override def fromFuture[T]: Future[T] => Future[Either[T, Throwable]] = {
      implicit val ctx: ExecutionContext = SynchronousExecutionContextAndIORuntime.syncEc
      transform(_)
    }

  }

}
