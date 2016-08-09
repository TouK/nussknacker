package pl.touk.esp.engine

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.compiledgraph.expression._
import pl.touk.esp.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.esp.engine.compiledgraph.service._
import pl.touk.esp.engine.compiledgraph.variable._

import scala.concurrent.{ExecutionContext, Future}

class Interpreter(config: InterpreterConfig) {

  def interpret(node: Node, metaData: MetaData, input: Any, inputParamName: String = InputParamName)
               (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    val ctx = ContextImpl(metaData).withVariable(inputParamName, input)
    interpret(node, ctx)
  }
  
  def interpret(node: Node, ctx: Context)
               (implicit executor: ExecutionContext): Future[InterpretationResult] =
    interpretNode(node, ctx)

  private def interpretNode(node: Node, ctx: Context)
                           (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    config.listeners.foreach(_.nodeEntered(node.id, ctx))
    node match {
      case Source(_, next) =>
        interpretNext(next, ctx)
      case VariableBuilder(_, varName, fields, next) =>
        interpretNext(next, createOrUpdateVariable(ctx, varName, fields))
      case Processor(_, ref, next) =>
        invoke(ref, ctx).flatMap(_ => interpretNext(next, ctx))
      case Enricher(_, ref, outName, next) =>
        invoke(ref, ctx).flatMap(out => interpretNext(next, ctx.withVariable(outName, out)))
      case Filter(_, expression, nextTrue, nextFalse) =>
        val result = evaluate[Boolean](expression, ctx)
        if (result)
          interpretNext(nextTrue, ctx)
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
            interpretNext(nextNode, ctx)
          case None =>
            interpretOptionalNext(node, defaultNext, ctx)
        }
      case AggregateDefinition(_, keyExpression, next) =>
        Future.successful(InterpretationResult(NextPartReference(next.id), evaluate(keyExpression, ctx), ctx))
      case AggregateTrigger(_, trigger, next) =>
        Future.successful(InterpretationResult(NextPartReference(next.id),
          trigger.map(evaluate[Boolean](_, ctx)).orNull, ctx))
      case Sink(_, optionalExpression) =>
        val output = optionalExpression match {
          case Some(expression) =>
            evaluate[Any](expression, ctx)
          case None =>
            outputValue(ctx)
        }
        Future.successful(InterpretationResult(EndReference, output, ctx))
    }
  }

  private def interpretOptionalNext(node: Node,
                                    optionalNext: Option[Next],
                                    ctx: Context)
                                   (implicit executionContext: ExecutionContext): Future[InterpretationResult] = {
    optionalNext match {
      case Some(next) => interpretNext(next, ctx)
      case None => Future.successful(InterpretationResult(DefaultSinkReference, outputValue(ctx), ctx))
    }
  }

  private def interpretNext(next: Next, ctx: Context)
                           (implicit executor: ExecutionContext): Future[InterpretationResult] =
    next match {
      case NextNode(node) => interpretNode(node, ctx)
      case PartRef(ref) => Future.successful(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx)) // output tutaj nie ma sensu
    }

  private def outputValue(ctx: Context) =
    ctx.getOrElse(OutputParamName, new java.util.HashMap[String, Any]())

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

  private def evaluate[R](expr: Expression, ctx: Context) = {
    val result = expr.evaluate[R](ctx)
    config.listeners.foreach(_.expressionEvaluated(expr.original, ctx, result))
    result
  }

}

object Interpreter {

  final val InputParamName = "input"
  final val OutputParamName = "output"

  private[engine] case class ContextImpl(processMetaData: MetaData,
                                         override val variables: Map[String, Any] = Map.empty) extends Context {

    def withVariable(name: String, value: Any): Context =
      copy(variables = variables + (name -> value))
  }

}