package pl.touk.nussknacker.engine

import cats.{Applicative, Id, Monad}
import cats.effect.IO
import cats.syntax.all._
import pl.touk.nussknacker.engine.Interpreter._
import pl.touk.nussknacker.engine.RuntimeMode.Test
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.ProcessListener.Transition.{
  DirectTransition,
  TransitionFromFragmentStartToNodeAfterFragment
}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ComponentUseContext, ServiceExecutionContext}
import pl.touk.nussknacker.engine.compiledgraph.node._
import pl.touk.nussknacker.engine.compiledgraph.service._
import pl.touk.nussknacker.engine.compiledgraph.variable._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.node.NodeComponentInfoExtractor
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

// TODO Interpreter and things around for sake of clarity of responsibilities between modules should be moved to the separate
//      module used only on runtime side. In the scenario-compiler module would stay only things responsible for
//      for compilation of graph into the runtime logic of components referenced by nodes
private class InterpreterInternal[F[_]: Monad](
    listeners: Seq[ProcessListener],
    expressionEvaluator: ExpressionEvaluator,
    interpreterShape: InterpreterShape[F],
    runtimeMode: RuntimeMode,
    serviceExecutionContext: ServiceExecutionContext,
    nodesDeploymentData: NodesDeploymentData,
)(implicit jobData: JobData) {

  type Result[T] = Either[T, NuExceptionInfo]

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

  private def handleError(node: Node, ctx: Context): Throwable => NuExceptionInfo = {
    NuExceptionInfo(Some(NodeComponentInfoExtractor.fromCompiledNode(node)), _, ctx)
  }

  private def onDirectTransitionToNextNode(nodeId: NodeId, nextNode: Next, ctx: Context): Unit = {
    val transition = DirectTransition(nodeId, nextNodeId(nextNode))
    listeners.foreach(_.transitionToNextNode(transition, ctx, jobData.metaData))
  }

  private def onTransitionFromFragmentStartToNodeAfterFragment(nodeId: NodeId, nextNode: Next, ctx: Context): Unit = {
    val transition = TransitionFromFragmentStartToNodeAfterFragment(nodeId, nextNodeId(nextNode))
    listeners.foreach(_.transitionToNextNode(transition, ctx, jobData.metaData))
  }

  private def nextNodeId(nextNode: Next): NodeId = {
    nextNode match {
      case NextNode(BranchEnd(definition)) => NodeId(definition.joinId)
      case other                           => NodeId(other.id)
    }
  }

  private def onProcessingFinishedInNode(nodeId: NodeId, ctx: Context): Unit = {
    listeners.foreach(_.processingFinishedInNode(nodeId, ctx, jobData.metaData))
  }

  private def interpretNode(node: Node, ctx: Context): F[List[Result[InterpretationResult]]] = {
    implicit val nodeImplicit: Node = node
    node match {
      // We do not invoke listener 'nodeEntered' here for nodes which are wrapped in PartRef by ProcessSplitter.
      // These are handled in interpretNext method
      case CustomNode(_, _, _, _) | EndingCustomNode(_, _, _) | Sink(_, _, _, _) | FragmentOutput(_, _, _, _) =>
      case _ => listeners.foreach(_.nodeEntered(NodeId(node.id), ctx, jobData.metaData))
    }
    node match {
      case Source(_, _, _, next) =>
        interpretOptionalNext(node, next, ctx)
      case VariableBuilder(_, _, _, _, next, unsetVariables) if unsetVariables.nonEmpty =>
        interpretOptionalNext(node, next, ctx.withoutVariables(unsetVariables))
      case VariableBuilder(_, _, varName, Right(fields), next, _) =>
        val variable = createOrUpdateVariable(ctx, varName, fields)
        interpretOptionalNext(node, next, variable)
      case VariableBuilder(_, _, varName, Left(expression), next, _) =>
        val valueWithModifiedContext = expressionEvaluator.evaluate[Any](expression, varName, NodeId(node.id), ctx)
        interpretOptionalNext(node, next, ctx.withVariable(varName, valueWithModifiedContext.value))
      case FragmentUsageStart(_, _, params, next) =>
        val (newCtx, vars) = expressionEvaluator.evaluateParameters(params, ctx)
        interpretOptionalNext(
          node,
          next,
          newCtx.pushNewContext(vars.map { case (paramName, value) => (paramName.value, value) })
        )
      case FragmentUsageEnd(_, _, fragmentUsageStartNodeId, outputVar, next) =>
        // Here we need parent context so we can compile rest of scenario. Unfortunately some component inside fragment
        // could've cleared that context. In that case, we take current (fragment's) context so we can keep the id,
        // clear it's variables, and keep using it in further processing.
        val parentContext = ctx.parentContext.getOrElse(ctx.copy(variables = Map.empty))
        val newParentContext = outputVar match {
          case Some(FragmentOutputVarDefinition(varName, fields)) =>
            val parsedFieldsMap = evaluateFragmentOutput(ctx, fields)
            parentContext.withVariable(varName, parsedFieldsMap)
          case None => parentContext
        }
        // This part is responsible for reporting the transition between the node that represents the entire fragment and the next node
        // - the `interpretOptionalNext` method triggers only reporting of the transition between the last node of the fragment graph and the next node
        // - we have to also report the transition between the single node representing the entire fragment and the next node
        next match {
          case Some(next) =>
            onTransitionFromFragmentStartToNodeAfterFragment(NodeId(fragmentUsageStartNodeId), next, newParentContext)
          case None =>
            onProcessingFinishedInNode(NodeId(fragmentUsageStartNodeId), newParentContext)
        }
        interpretOptionalNext(node, next, newParentContext)
      case Processor(_, _, ref, next, false) =>
        invokeWrappedInInterpreterShape(ref, ctx).flatMap {
          // for Processor the result is null/BoxedUnit/Void etc. so we ignore it
          case Left(ValueWithContext(_, newCtx)) =>
            interpretOptionalNext(node, next, newCtx)
          case Right(exInfo) =>
            Monad[F].pure(List(Right(exInfo)))
        }
      case Processor(_, _, _, next, true) =>
        interpretOptionalNext(node, next, ctx)
      case EndingProcessor(id, _, ref, false) =>
        listeners.foreach(_.endEncountered(NodeId(id), ref.id, ctx, jobData.metaData))
        invokeWrappedInInterpreterShape(ref, ctx).map {
          // for Processor the result is null/BoxedUnit/Void etc. so we ignore it
          case Left(ValueWithContext(_, newCtx)) =>
            interpretationResult[Id](node, EndReference(id), newCtx)
          case Right(exInfo) => List(Right(exInfo))
        }
      case EndingProcessor(id, _, _, true) =>
        interpretationResult(node, EndReference(id), ctx)
      case FragmentOutput(id, _, _, true) =>
        interpretationResult(node, FragmentEndReference(id, Map.empty), ctx)
      case FragmentOutput(id, _, fieldsWithExpression, false) =>
        fieldsWithExpression.toList
          .traverse(a =>
            Either
              .catchNonFatal(a._1 -> expressionEvaluator.evaluate(a._2.expression, a._1, NodeId(id), ctx).value)
              .toValidatedNel
          )
          .map(_.toMap)
          .fold(
            exceptions => {
              listeners.foreach(_.nodeEntered(NodeId(node.id), ctx, jobData.metaData))
              Monad[F].pure(exceptions.toList.map(exc => Right(handleError(node, ctx)(exc))))
            },
            fields => {
              val newCtx = ctx.withVariables(fields)
              listeners.foreach(_.nodeEntered(NodeId(node.id), newCtx, jobData.metaData))
              interpretationResult(node, FragmentEndReference(id, fields), newCtx)
            }
          )
      case Enricher(_, _, _, outName, next, Some(mockedOutput)) if runtimeMode == Test =>
        val valueWithModifiedContext = expressionEvaluator.evaluate[Any](mockedOutput, outName, NodeId(node.id), ctx)
        interpretOptionalNext(node, next, ctx.withVariable(outName, valueWithModifiedContext.value))
      case Enricher(_, _, ref, outName, next, _) =>
        invokeWrappedInInterpreterShape(ref, ctx).flatMap {
          case Left(ValueWithContext(out, newCtx)) =>
            interpretOptionalNext(node, next, newCtx.withVariable(outName, out))
          case Right(exInfo) => Monad[F].pure(List(Right(exInfo)))
        }
      case Filter(_, _, expression, nextTrue, nextFalse, disabled) =>
        val valueWithModifiedContext =
          if (disabled) ValueWithContext(true, ctx) else evaluateExpression[Boolean](expression, ctx, expressionName)
        if (disabled || valueWithModifiedContext.value)
          interpretOptionalNext(node, nextTrue, valueWithModifiedContext.context)
        else
          interpretOptionalNext(node, nextFalse, valueWithModifiedContext.context)
      case Switch(_, _, expr, nexts, defaultNext) =>
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
                (valueWithModifiedContext.context, casee.next)
              } else {
                (valueWithModifiedContext.context, None)
              }
            case a => a
          }
        } match {
          case (accCtx, Some(nextNode)) =>
            interpretNext(node, nextNode, accCtx)
          case (accCtx, None) =>
            interpretOptionalNext(node, defaultNext, accCtx)
        }
      case Sink(id, _, _, true) =>
        sinkInterpretationResult(node, EndReference(id), ctx)
      case Sink(id, _, ref, false) =>
        listeners.foreach(_.endEncountered(NodeId(id), ref, ctx, jobData.metaData))
        sinkInterpretationResult(node, EndReference(id), ctx)
      case BranchEnd(e) =>
        Monad[F].pure(List(Left(InterpretationResult(e.joinReference, ctx))))
      case CustomNode(_, _, _, next) =>
        interpretOptionalNext(node, next, ctx)
      case EndingCustomNode(id, _, ref) =>
        listeners.foreach(_.endEncountered(NodeId(id), ref, ctx, jobData.metaData))
        interpretationResult(node, EndReference(id), ctx)
      case SplitNode(_, _, Nil) =>
        onProcessingFinishedInNode(NodeId(node.id), ctx)
        Applicative[F].pure(List.empty)
      case SplitNode(_, _, nexts) =>
        import cats.implicits._
        nexts
          .map(next => interpretNext(node, next, ctx.withContextIdPathPart(NodeId(node.id), next.id)))
          .sequence
          .map(_.flatten)
    }
  }

  private def sinkInterpretationResult(
      node: Node,
      reference: PartReference,
      ctx: Context
  ): F[List[Result[InterpretationResult]]] = {
    onProcessingFinishedInNode(NodeId(node.id), ctx)
    Monad[F].pure(List(Left(InterpretationResult(reference, ctx))))
  }

  private def interpretationResult[FF[_]: Applicative](
      node: Node,
      reference: PartReference,
      ctx: Context
  ): FF[List[Result[InterpretationResult]]] = {
    onProcessingFinishedInNode(NodeId(node.id), ctx)
    Applicative[FF].pure(List(Left(InterpretationResult(reference, ctx))))
  }

  private def interpretOptionalNext(
      node: Node,
      optionalNext: Option[Next],
      ctx: Context
  ): F[List[Result[InterpretationResult]]] = {
    optionalNext match {
      case Some(next) =>
        interpretNext(node, next, ctx)
      case None =>
        // todo: perhaps we should refactor the way DeadEndingData is used, all nodes can be dead ends now
        node match {
          case Filter(_, _, _, _, _, _) | Switch(_, _, _, _, _) =>
            listeners.foreach(_.deadEndEncountered(NodeId(node.id), ctx, jobData.metaData))
          case _ =>
            ()
        }
        interpretationResult(node, DeadEndReference(node.id), ctx)
    }
  }

  private def interpretNext(node: Node, next: Next, ctx: Context): F[List[Result[InterpretationResult]]] = {
    onDirectTransitionToNextNode(NodeId(node.id), next, ctx)
    next match {
      case NextNode(node) =>
        interpret(node, ctx)
      case pr @ PartRef(ref) =>
        listeners.foreach(_.nodeEntered(NodeId(pr.id), ctx, jobData.metaData))
        Monad[F].pure(List(Left(InterpretationResult(NextPartReference(ref), ctx))))
    }
  }

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field])(
      implicit node: Node
  ): Context = {
    val contextWithInitialVariable =
      ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))

    fields.foldLeft(contextWithInitialVariable) { case (context, field) =>
      val valueWithContext = expressionEvaluator.evaluate[Any](field.expression, field.name, NodeId(node.id), context)
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

  // We need java HashMap here because spel evaluator will fail on scala Map
  private def evaluateFragmentOutput(ctx: Context, fields: Seq[Field])(
      implicit node: Node
  ): java.util.HashMap[String, Any] = {
    {
      import scala.jdk.CollectionConverters._
      val fieldsMap = fields
        .map(field =>
          (field.name, expressionEvaluator.evaluate[Any](field.expression, field.name, NodeId(node.id), ctx).value)
        )
        .toMap
        .asJava

      new java.util.HashMap[String, Any](fieldsMap)
    }
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
    val nodeDeploymentData                                = nodesDeploymentData.get(NodeId(node.id))
    implicit val componentUseContext: ComponentUseContext = runtimeMode.createContext(nodeDeploymentData)
    val resultFuture                                      = ref.invoke(ctx, serviceExecutionContext)
    import SynchronousExecutionContextAndIORuntime.syncEc
    resultFuture.onComplete { result =>
      listeners.foreach(_.serviceInvoked(NodeId(node.id), ref.id, ctx, jobData.metaData, result))
    }
    resultFuture.map(ValueWithContext(_, ctx))
  }

  private def evaluateExpression[R](expr: CompiledExpression, ctx: Context, name: String)(
      implicit node: Node
  ): ValueWithContext[R] = {
    expressionEvaluator.evaluate(expr, name, NodeId(node.id), ctx)
  }

}

class Interpreter(
    listeners: Seq[ProcessListener],
    expressionEvaluator: ExpressionEvaluator,
    runtimeMode: RuntimeMode,
    nodesDeploymentData: NodesDeploymentData,
) {

  def interpret[F[_]](
      node: Node,
      jobData: JobData,
      ctx: Context,
      serviceExecutionContext: ServiceExecutionContext
  )(
      implicit monad: Monad[F],
      shape: InterpreterShape[F],
  ): F[List[Either[InterpretationResult, NuExceptionInfo]]] = {
    implicit val jobDataImplicit: JobData = jobData
    new InterpreterInternal[F](
      listeners,
      expressionEvaluator,
      shape,
      runtimeMode,
      serviceExecutionContext,
      nodesDeploymentData,
    )
      .interpret(node, ctx)
  }

}

object Interpreter {

  def apply(
      listeners: Seq[ProcessListener],
      expressionEvaluator: ExpressionEvaluator,
      runtimeMode: RuntimeMode,
      nodesDeploymentData: NodesDeploymentData,
  ): Interpreter = {
    new Interpreter(listeners, expressionEvaluator, runtimeMode, nodesDeploymentData)
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
