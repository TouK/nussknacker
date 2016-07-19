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
      case StartNode(_, next) => interpretNode(next, ctx)
      case Processor(_, ref, next) => invoke(ref, ctx).flatMap(_ => interpretNode(next, ctx))
      case Enricher(_, ref, outName, next) => invoke(ref, ctx).flatMap(out => interpretNode(next, ctx.withData(outName, out)))
      case Filter(_, expression, nextTrue, nextFalse) =>
        if (expression.evaluate(ctx))
          interpretNode(nextTrue, ctx)
        else nextFalse.map(node => interpretNode(node, ctx)).getOrElse(Future((None, ctx)))
      case Switch(_, expression, exprVal, nexts, defaultResult) => val output = expression.evaluate[Any](ctx)
        val newCtx = ctx.withData(exprVal, output)
        nexts.view.find {
          case (expr, _) => expr.evaluate(newCtx)
        } match {
          case Some((_, nextNode)) => interpretNode(nextNode, ctx)
          case None => Future((defaultResult.map(_.evaluate[Any](ctx)), ctx))
        }
      case End(_, expr) => Future((expr.map(_.evaluate[Any](ctx)), ctx))
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
