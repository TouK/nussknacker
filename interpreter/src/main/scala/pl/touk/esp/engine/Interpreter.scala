package pl.touk.esp.engine

import java.lang.reflect.Method

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.api.{Context, InterpretationResult, MetaData, ProcessListener}
import pl.touk.esp.engine.graph._
import pl.touk.esp.engine.graph.expression._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service._
import pl.touk.esp.engine.graph.variable._
import pl.touk.esp.engine.validate.GraphValidator

import scala.concurrent.{ExecutionContext, Future}

class Interpreter(config: InterpreterConfig) {

  def interpret(process: EspProcess, input: Any)
               (implicit executor : ExecutionContext): Future[InterpretationResult] = {
    GraphValidator.validate(process).toXor.valueOr(errors => throw new IllegalArgumentException(s"Find errors: $errors"))
    val ctx = ContextImpl(config, process.metaData).withVariable(InputParamName, input)
    interpretNode(process.root, ctx).map {
      case NodeInterpretationResult(sinkRef, finalCtx) =>
        InterpretationResult(sinkRef, finalCtx.getOrElse(OutputParamName, new java.util.HashMap[String, Any]()))
    }
  }

  private def interpretNode(node: Node, ctx: ContextImpl)
                           (implicit executor : ExecutionContext): Future[NodeInterpretationResult] = {
    ctx.listeners.foreach(_.nodeEntered(node.id, ctx))
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
      case Sink(_, ref, optionalExpression) =>
        val newCtx = optionalExpression match {
          case Some(expression) =>
            ctx.withVariable(OutputParamName, evaluate[Any](expression, ctx))
          case None =>
            ctx
        }
        Future.successful(NodeInterpretationResult(Some(ref), newCtx))
    }
  }

  private def createOrUpdateVariable(ctx: ContextImpl, varName: String, fields: Seq[Field]): ContextImpl = {
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

  private def invoke(ref: ServiceRef, ctx: ContextImpl)
                    (implicit executionContext: ExecutionContext): Future[Any] = {
    val preparedParams = ref.parameters
      .map(param => param.name -> param.expression.evaluate(ctx)).toMap
    val resultFuture = ctx.service(ref.id).invoke(preparedParams)
    resultFuture.onComplete { result =>
      ctx.listeners.foreach(_.serviceInvoked(ref.id, ctx, result))
    }
    resultFuture
  }

  private def evaluate[R](expr: Expression, ctx: ContextImpl) = {
    val result = expr.evaluate[R](ctx)
    ctx.listeners.foreach(_.expressionEvaluated(expr.original, ctx, result))
    result
  }

  private def interpretOptionalNext(node: Node,
                                    optionalNext: Option[Node],
                                    ctx: ContextImpl)
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

  private case class NodeInterpretationResult(sinkRef: Option[SinkRef], context: ContextImpl)
  
  private[engine] case class ContextImpl(config: InterpreterConfig,
                                         processMetaData: MetaData,
                                         override val variables: Map[String, Any] = Map.empty) extends Context {

    def listeners: Seq[ProcessListener] =
      config.listeners

    def expressionFunctions: Map[String, Method] =
      config.expressionFunctions

    def service(id: String) = config.services.getOrElse(
      id,
      throw new RuntimeException(s"Missing service: $id")
    )

    def modifyVariable[T](name: String, f: T => T): ContextImpl =
      withVariable(name, f(apply(name)))

    def modifyOptionalVariable[T](name: String, f: Option[T] => T): ContextImpl =
      withVariable(name, f(get[T](name)))

    def withVariable(name: String, value: Any): ContextImpl =
      copy(variables = variables + (name -> value))
  }

}