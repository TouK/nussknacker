package pl.touk.esp.engine

import pl.touk.esp.engine.Interpreter._
import pl.touk.esp.engine.api.InterpreterMode._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.compiledgraph.expression._
import pl.touk.esp.engine.compiledgraph.node.{Sink, Source, _}
import pl.touk.esp.engine.compiledgraph.service._
import pl.touk.esp.engine.compiledgraph.variable._

import scala.concurrent.{ExecutionContext, Future}

class Interpreter(config: InterpreterConfig) {

  def interpret(node: Node,
                mode: InterpreterMode,
                metaData: MetaData,
                input: Any, inputParamName: String = InputParamName)
               (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    val ctx = ContextImpl(metaData).withVariable(inputParamName, input)
    interpret(node, mode, ctx)
  }
  
  def interpret(node: Node, mode: InterpreterMode, ctx: Context)
               (implicit executor: ExecutionContext): Future[InterpretationResult] = {
    implicit val implMode = mode
    interpretNode(node, ctx)
  }

  private def interpretNode(node: Node, ctx: Context)
                           (implicit mode: InterpreterMode, executor: ExecutionContext): Future[InterpretationResult] = {
    config.listeners.foreach(_.nodeEntered(node.id, ctx, mode))
    (node, mode) match {
      case (Source(_, next), Traverse) =>
        interpretNext(next, ctx)
      case (VariableBuilder(_, varName, fields, next), Traverse) =>
        interpretNext(next, createOrUpdateVariable(ctx, varName, fields))
      case (Processor(_, ref, next), Traverse) =>
        invoke(ref, ctx).flatMap(_ => interpretNext(next, ctx))
      case (EndingProcessor(_, ref), Traverse) =>
        invoke(ref, ctx).map { output =>
          InterpretationResult(EndReference, output, ctx)
        }
      case (Enricher(_, ref, outName, next), Traverse) =>
        invoke(ref, ctx).flatMap(out => interpretNext(next, ctx.withVariable(outName, out)))
      case (Filter(_, expression, nextTrue, nextFalse), Traverse) =>
        val result = evaluate[Boolean](expression, ctx)
        if (result)
          interpretNext(nextTrue, ctx)
        else
          interpretOptionalNext(node, nextFalse, ctx)
      case (Switch(_, expression, exprVal, nexts, defaultNext), Traverse) =>
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
      case (agg: Aggregate, Traverse) =>
        interpretNext(agg.next, ctx)
      case (agg: Aggregate, AggregateKeyExpression) =>
        Future.successful(InterpretationResult(EndReference, evaluate(agg.keyExpression, ctx), ctx))
      case (agg: Aggregate, AggregateTriggerExpression) =>
        Future.successful(InterpretationResult(
          EndReference,
          agg.triggerExpression.map(evaluate[Boolean](_, ctx)).orNull,
          ctx))
      case (Sink(_, optionalExpression), Traverse) =>
        val output = optionalExpression match {
          case Some(expression) =>
            evaluate[Any](expression, ctx)
          case None =>
            outputValue(ctx)
        }
        Future.successful(InterpretationResult(EndReference, output, ctx))
      case (_, AggregateKeyExpression | AggregateTriggerExpression) =>
        throw new IllegalArgumentException(s"Mode $mode make no sense for node: ${node.getClass.getName}")
    }
  }

  private def interpretOptionalNext(node: Node,
                                    optionalNext: Option[Next],
                                    ctx: Context)
                                   (implicit mode: InterpreterMode,
                                    executionContext: ExecutionContext): Future[InterpretationResult] = {
    optionalNext match {
      case Some(next) => interpretNext(next, ctx)
      case None => Future.successful(InterpretationResult(DefaultSinkReference, outputValue(ctx), ctx))
    }
  }

  private def interpretNext(next: Next, ctx: Context)
                           (implicit mode: InterpreterMode, executor: ExecutionContext): Future[InterpretationResult] =
    next match {
      case NextNode(node) => interpretNode(node, ctx)
      case PartRef(ref) => Future.successful(InterpretationResult(NextPartReference(ref), outputValue(ctx), ctx))
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