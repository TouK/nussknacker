package pl.touk.process.model

import pl.touk.process.model.api.Ctx
import pl.touk.process.model.graph.node._
import pl.touk.process.model.graph.processor._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object Interpreter {

  def interpret(node: Node, ctx: Ctx)
               (implicit executor : ExecutionContext): Future[(Option[Any], Ctx)] = {
    ProcessValidator.validate(node).toXor.valueOr(errors => throw new IllegalArgumentException(s"Find errors: $errors"))
    interpretNode(node, ctx)
  }

  private def interpretNode(node: Node, ctx: Ctx)
                           (implicit executor : ExecutionContext): Future[(Option[Any], Ctx)] = {
    ctx.listeners.foreach(_.nodeEntered(ctx, node))
    ctx.log(s"Processing node ${node.metaData.id}")
    node match {
      case StartNode(_, next) =>
        interpretNode(next, ctx)
      case Processor(_, ref, next) =>
        invoke(ref, ctx).flatMap(_ => interpretNode(next, ctx))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap(out => interpretNode(next, ctx.withData(outName, out)))
      case Filter(_, expression, nextTrue, nextFalse) =>
        val result = expression.evaluate[Boolean](ctx)
        ctx.listeners.foreach(_.expressionEvaluated(expression, result))
        if (result)
          interpretNode(nextTrue, ctx)
        else
          nextFalse.map(node => interpretNode(node, ctx)).getOrElse(Future.successful((None, ctx)))
      case Switch(_, expression, exprVal, nexts, defaultResult) =>
        val output = expression.evaluate[Any](ctx)
        ctx.listeners.foreach(_.expressionEvaluated(expression, output))
        val newCtx = ctx.withData(exprVal, output)
        nexts.view.find {
          case (expr, _) =>
            val result = expr.evaluate[Boolean](newCtx)
            ctx.listeners.foreach(_.expressionEvaluated(expr, result))
            result
        } match {
          case Some((_, nextNode)) => interpretNode(nextNode, ctx)
          case None => Future.successful((defaultResult.map(_.evaluate[Any](ctx)), ctx))
        }
      case End(_, expr) =>
        val optionalResult = expr.map { e =>
          val result = e.evaluate[Any](ctx)
          ctx.listeners.foreach(_.expressionEvaluated(e, result))
          result
        }
        Future.successful((optionalResult, ctx))
    }
  }

  private def invoke(ref: ProcessorRef, ctx: Ctx)(implicit executionContext: ExecutionContext): Future[Any] = {
    val preparedCtx = ref.parameters
      .map(param => param.name -> param.expression.evaluate(ctx)).toMap
    val resultFuture = ctx.services(ref.id).invoke(preparedCtx, ctx)
    resultFuture.onSuccess {
      case result => ctx.listeners.foreach(_.serviceInvoked(ref.id, result))
    }
    resultFuture
  }

}
