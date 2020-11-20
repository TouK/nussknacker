package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{LazyParameter, definition, _}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

private[definition] trait CompilerLazyParameter[T <: AnyRef] extends LazyParameter[T] {

  //TODO: get rid of Future[_] as we evaluate parameters synchronously...
  def prepareEvaluator(deps: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T]

}

// This class is public for tests purpose. Be aware that its interface can be changed in the future
case class ExpressionLazyParameter[T <: AnyRef](nodeId: NodeId,
                                                parameterDef: definition.Parameter,
                                                expression: Expression,
                                                returnType: TypingResult) extends CompilerLazyParameter[T] {
  override def prepareEvaluator(compilerInterpreter: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = {
    val compiledExpression = compilerInterpreter.deps.expressionCompiler
              .compileWithoutContextValidation(expression, parameterDef.name, parameterDef.typ)(nodeId)
              .valueOr(err => throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}"))
    val evaluator = compilerInterpreter.deps.expressionEvaluator
    val compiledParameter = compiledgraph.evaluatedparam.Parameter(TypedExpression(compiledExpression, Unknown, null), parameterDef)
    context: Context => Future.successful(evaluator.evaluateParameter(compiledParameter, context)(nodeId, compilerInterpreter.metaData)).map(_.value.asInstanceOf[T])(ec)
  }
}

private[definition] case class ProductLazyParameter[T <: AnyRef, Y <: AnyRef](arg1: LazyParameter[T], arg2: LazyParameter[Y]) extends CompilerLazyParameter[(T, Y)] {

  override def returnType: TypingResult = Typed.genericTypeClass[(T, Y)](List(arg1.returnType, arg2.returnType))

  override def prepareEvaluator(lpi: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[(T, Y)] = {
    val arg1Interpreter = lpi.createInterpreter(ec, arg1)
    val arg2Interpreter = lpi.createInterpreter(ec, arg2)
    ctx: Context =>
      val arg1Value = arg1Interpreter(ctx)
      val arg2Value = arg2Interpreter(ctx)
      arg1Value.flatMap(left => arg2Value.map((left, _)))
  }
}

private[definition] case class MappedLazyParameter[T <: AnyRef, Y <: AnyRef](arg: LazyParameter[T], fun: T => Y, returnType: TypingResult) extends CompilerLazyParameter[Y] {

  override def prepareEvaluator(lpi: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argInterpreter = lpi.createInterpreter(ec, arg)
    ctx: Context => argInterpreter(ctx).map(fun)
  }
}

// This class is public for tests purpose. Be aware that its interface can be changed in the future
case class FixedLazyParameter[T <: AnyRef](value: T, returnType: TypingResult) extends CompilerLazyParameter[T] {

  override def prepareEvaluator(deps: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = _ => Future.successful(value)

}


trait CompilerLazyParameterInterpreter extends LazyParameterInterpreter {

  def deps: LazyInterpreterDependencies

  def metaData: MetaData

  override def createInterpreter[T <: AnyRef](lazyInterpreter: LazyParameter[T]): (ExecutionContext, Context) => Future[T]
    = (ec: ExecutionContext, context: Context) => createInterpreter(ec, lazyInterpreter)(context)

  override def product[A <: AnyRef, B <: AnyRef](fa: LazyParameter[A], fb: LazyParameter[B]): LazyParameter[(A, B)] = {
    ProductLazyParameter(fa, fb)
  }

  override def map[T <: AnyRef, Y <: AnyRef](parameter: LazyParameter[T], funArg: T => Y, outputTypingResult: TypingResult): LazyParameter[Y] =
    new MappedLazyParameter[T, Y](parameter, funArg, outputTypingResult)

  override def pure[T <: AnyRef](value: T, valueTypingResult: TypingResult): LazyParameter[T] = FixedLazyParameter(value, valueTypingResult)

  //it's important that it's (...): (Context => Future[T])
  //and not e.g. (...)(Context) => Future[T] as we want to be sure when body is evaluated (in particular expression compilation)!
  private[definition] def createInterpreter[T <: AnyRef](ec: ExecutionContext, definition: LazyParameter[T]): Context => Future[T] = {
    definition match {
      case e:CompilerLazyParameter[T] => e.prepareEvaluator(this)(ec)
      case _ => throw new IllegalArgumentException(s"LazyParameter $definition is not supported")
    }
  }

  override def syncInterpretationFunction[T <: AnyRef](lazyInterpreter: LazyParameter[T]): Context => T = {

    implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
    val interpreter = createInterpreter(ec, lazyInterpreter)
    v1: Context => Await.result(interpreter(v1), deps.processTimeout)
  }

}

case class LazyInterpreterDependencies(expressionEvaluator: ExpressionEvaluator,
                                       expressionCompiler: ExpressionCompiler,
                                       processTimeout: FiniteDuration) extends Serializable

object CustomStreamTransformerExtractor extends AbstractMethodDefinitionExtractor[CustomStreamTransformer] {

  override protected val expectedReturnType: Option[Class[_]] = None

  override protected val additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[NodeId])

}



