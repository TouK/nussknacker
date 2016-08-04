package pl.touk.esp.engine

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.compiledgraph.expression._
import pl.touk.esp.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.esp.engine.compiledgraph.service._
import pl.touk.esp.engine.compiledgraph.variable._

import scala.concurrent.{ExecutionContext, Future}

class Interpreter(config: InterpreterConfig) {

  def interpret(metaData: MetaData, compiledNode: Node, input: Any, inputVarName: String = InputParamName)
               (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    val ctx = ContextImpl(metaData).withVariable(inputVarName, input)
    interpret(compiledNode, ctx)
  }


  private def interpret(start: Node, ctx: InterpreterContext)
                       (implicit executor: ExecutionContext): Future[InterpretationResult] =
    interpretNode(start, ctx).map {
      case NodeInterpretationResult(ref, finalCtx) =>
        InterpretationResult(ref,
          finalCtx.getOrElse(OutputParamName, new java.util.HashMap[String, Any]()), finalCtx)
    }

  private def interpretNode(node: Node, ctx: InterpreterContext)
                           (implicit executor: ExecutionContext): Future[NodeInterpretationResult] = {
    config.listeners.foreach(_.nodeEntered(node.id, ctx))
    node match {
      case Source(_, _, next) =>
        interpretNode(next, ctx)
      case VariableBuilder(_, varName, fields, next) =>
        interpretNode(next, createOrUpdateVariable(ctx, varName, fields))
      case Processor(_, ref, next) =>
        invoke(ref, ctx).flatMap(_ => interpretNode(next, ctx))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap(out => interpretNode(next, ctx.withVariable(outName, out)))
      case Filter(_, expression, nextTrue, nextFalse) =>
        val result = evaluate[Boolean](expression, ctx)
        if (result)
          interpretNode(nextTrue, ctx)
        else
          interpretOptionalNext(node, nextFalse, ctx)
      case Switch(_, expression, exprVal, nexts, defaultNext) =>
        val output = evaluate[Any](expression, ctx)
        val newCtx = ctx.withVariable(exprVal, output)
        nexts.view.find {
          case Case(expr, _) =>
            evaluate[Boolean](expr, newCtx)
        } match {
          case Some(Case(_, nextNode)) =>
            interpretNode(nextNode, ctx)
          case None =>
            interpretOptionalNext(node, defaultNext, ctx)
        }
      case Sink(id, ref, optionalExpression) =>
        val newCtx = optionalExpression match {
          case Some(expression) =>
            ctx.withVariable(OutputParamName, evaluate[Any](expression, ctx))
          case None =>
            ctx
        }
        Future.successful(NodeInterpretationResult(Some(PartReference(id)), newCtx))
      case Aggregate(id) =>
        //TODO: output?
        Future.successful(NodeInterpretationResult(Some(PartReference(id)), ctx))
    }
  }

  private def createOrUpdateVariable(ctx: InterpreterContext, varName: String, fields: Seq[Field]): InterpreterContext = {
    val contextWithInitialVariable = ctx.modifyOptionalVariable[java.util.Map[String, Any]](varName, _.getOrElse(new java.util.HashMap[String, Any]()))
    fields.foldLeft(contextWithInitialVariable) {
      case (context, field) =>
        context.modifyVariable[java.util.Map[String, Any]](varName, { m =>
          val newMap = new java.util.HashMap[String, Any](m)
          newMap.put(field.name, evaluate[Any](field.expression, context))
          newMap
        })
    }
  }

  private def invoke(ref: ServiceRef, ctx: InterpreterContext)
                    (implicit executionContext: ExecutionContext): Future[Any] = {
    val preparedParams = ref.parameters
      .map(param => param.name -> param.expression.evaluate(ctx)).toMap
    val service = config.services.getOrElse(
      ref.id,
      throw new RuntimeException(s"Missing service: ${ref.id}")
    )
    val resultFuture = service.invoke(preparedParams)
    resultFuture.onComplete { result =>
      config.listeners.foreach(_.serviceInvoked(ref.id, ctx, result))
    }
    resultFuture
  }

  private def evaluate[R](expr: Expression, ctx: InterpreterContext) = {
    val result = expr.evaluate[R](ctx)
    config.listeners.foreach(_.expressionEvaluated(expr.original, ctx, result))
    result
  }

  private def interpretOptionalNext(node: Node,
                                    optionalNext: Option[Node],
                                    ctx: InterpreterContext)
                                   (implicit executionContext: ExecutionContext): Future[NodeInterpretationResult] = {
    optionalNext match {
      case Some(next) => interpretNode(next, ctx)
      case None => Future.successful(NodeInterpretationResult(None, ctx))
    }
  }

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"

  private case class NodeInterpretationResult(reference: Option[PartReference], context: Context)

  private[engine] case class ContextImpl(processMetaData: MetaData,
                                         override val variables: Map[String, Any] = Map.empty) extends InterpreterContext {

    def withVariable(name: String, value: Any): InterpreterContext =
      copy(variables = variables + (name -> value))
  }

}

trait InterpreterContext extends Context {

  def modifyVariable[T](name: String, f: T => T): InterpreterContext =
    withVariable(name, f(apply(name)))

  def modifyOptionalVariable[T](name: String, f: Option[T] => T): InterpreterContext =
    withVariable(name, f(get[T](name)))

  def withVariable(name: String, value: Any): InterpreterContext

}