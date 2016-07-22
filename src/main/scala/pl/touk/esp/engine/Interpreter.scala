package pl.touk.esp.engine

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.graph.expression._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service._
import pl.touk.esp.engine.graph.variable._
import pl.touk.esp.engine.validate.GraphValidator

import scala.concurrent.{ExecutionContext, Future}

class Interpreter(config: InterpreterConfig) {

  def interpret(node: StartNode, input: Any)
               (implicit executor : ExecutionContext): Future[InterpretationResult] = {
    GraphValidator.validate(node).toXor.valueOr(errors => throw new IllegalArgumentException(s"Find errors: $errors"))
    val ctx = Context(config).withVariable(InputParamName, input)
    interpretNode(node, ctx).map {
      case NodeInterpretationBroken =>
        InterpretationBroken
      case NodeInterpretationEndedUp(endNode, finalCtx) =>
        InterpretationEndedUp(endNode, finalCtx(OutputParamName))
    }
  }

  private def interpretNode(node: Node, ctx: Context)
                           (implicit executor : ExecutionContext): Future[NodeInterpretationResult] = {
    ctx.listeners.foreach(_.nodeEntered(node, ctx))
    node match {
      case StartNode(_, next) =>
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
      case end@End(_, optionalExpression) =>
        val newCtx = optionalExpression match {
          case Some(expression) =>
            ctx.withVariable(OutputParamName, evaluate[Any](expression, ctx))
          case None =>
            ctx
        }
        Future.successful(NodeInterpretationEndedUp(end, newCtx))
    }
  }

  private def createOrUpdateVariable(ctx: Context, varName: String, fields: Seq[Field]): Context = {
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

  private def invoke(ref: ServiceRef, ctx: Context)
                    (implicit executionContext: ExecutionContext): Future[Any] = {
    val preparedParams = ref.parameters
      .map(param => param.name -> param.expression.evaluate(ctx)).toMap
    val resultFuture = ctx.service(ref.id).invoke(preparedParams)
    resultFuture.onComplete { result =>
      ctx.listeners.foreach(_.serviceInvoked(ref.id, ctx, result))
    }
    resultFuture
  }

  private def evaluate[R](expr: Expression, ctx: Context) = {
    val result = expr.evaluate[R](ctx)
    ctx.listeners.foreach(_.expressionEvaluated(expr, ctx, result))
    result
  }

  private def interpretOptionalNext(node: Node,
                                    optionalNext: Option[Node],
                                    ctx: Context)
                                   (implicit executionContext: ExecutionContext): Future[NodeInterpretationResult] = {
    optionalNext match {
      case Some(next) => interpretNode(next, ctx)
      case None => Future.successful(NodeInterpretationBroken)
    }
  }

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"

  sealed trait InterpretationResult {
    def toOption: Option[InterpretationEndedUp]
  }
  case object InterpretationBroken extends InterpretationResult {
    override def toOption: Option[InterpretationEndedUp] = None
  }
  case class InterpretationEndedUp(endNode: End, output: Any) extends InterpretationResult {
    override def toOption: Option[InterpretationEndedUp] = Some(this)
  }

  private sealed trait NodeInterpretationResult
  private case object NodeInterpretationBroken extends NodeInterpretationResult
  private case class NodeInterpretationEndedUp(endNode: End, context: Context) extends NodeInterpretationResult

}