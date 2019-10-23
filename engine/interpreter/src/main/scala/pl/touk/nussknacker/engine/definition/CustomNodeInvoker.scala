package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.{LazyParameter, _}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.reflect.runtime.universe.TypeTag
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

private[definition] trait CompilerLazyParameter[T] extends LazyParameter[T] {

  def prepareEvaluator(deps: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T]

}

private[definition] case class ExpressionLazyParameter[T](nodeId: NodeId,
                                                          parameter: evaluatedparam.Parameter,
                                                          returnType: TypingResult) extends CompilerLazyParameter[T] {
  override def prepareEvaluator(compilerInterpreter: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = {
    val compiledExpression = compilerInterpreter.deps.expressionCompiler
              .compileWithoutContextValidation(parameter.expression, parameter.name)(nodeId)
              .valueOr(err => throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}"))
    val evaluator = compilerInterpreter.deps.expressionEvaluator
    context: Context => evaluator.evaluate[T](compiledExpression, parameter.name, nodeId.id, context)(ec, compilerInterpreter.metaData).map(_.value)(ec)
  }
}

private[definition] case class ProductLazyParameter[T, Y](arg1: LazyParameter[T], arg2: LazyParameter[Y]) extends CompilerLazyParameter[(T, Y)] {

  override def returnType: TypingResult = TypedClass(classOf[(T, Y)], List(arg1.returnType, arg2.returnType))

  override def prepareEvaluator(lpi: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[(T, Y)] = {
    val arg1Interpreter = lpi.createInterpreter(arg1)
    val arg2Interpreter = lpi.createInterpreter(arg2)
    ctx: Context =>
      val arg1Value = arg1Interpreter(ec, ctx)
      val arg2Value = arg2Interpreter(ec, ctx)
      arg1Value.flatMap(left => arg2Value.map((left, _)))
  }
}

private[definition] case class MappedLazyParameter[T, Y](arg: LazyParameter[T], fun: T => Y, returnType: TypingResult) extends CompilerLazyParameter[Y] {

  override def prepareEvaluator(lpi: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argInterpreter = lpi.createInterpreter(arg)
    ctx: Context => argInterpreter(ec, ctx).map(fun)
  }
}

private[definition] case class FixedLazyParameter[T](value: T, returnType: TypingResult) extends CompilerLazyParameter[T] {

  override def prepareEvaluator(deps: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = _ => Future.successful(value)

}


trait CompilerLazyParameterInterpreter extends LazyParameterInterpreter {

  def deps: LazyInterpreterDependencies

  def metaData: MetaData

  override def createInterpreter[T](lazyInterpreter: LazyParameter[T]): (ExecutionContext, Context) => Future[T]
    = (ec: ExecutionContext, context: Context) => createInterpreter(ec, lazyInterpreter)(context)

  override def product[A, B](fa: LazyParameter[A], fb: LazyParameter[B]): LazyParameter[(A, B)] = {
    ProductLazyParameter(fa, fb)
  }

  override def map[T, Y](parameter: LazyParameter[T], funArg: T => Y, outputTypingResult: TypingResult): LazyParameter[Y] =
    new MappedLazyParameter[T, Y](parameter, funArg, outputTypingResult)

  override def pure[T](value: T, valueTypingResult: TypingResult): LazyParameter[T] = FixedLazyParameter(value, valueTypingResult)

  //it's important that it's (...): (Context => Future[T])
  //and not e.g. (...)(Context) => Future[T] as we want to be sure when body is evaluated (in particular expression compilation)!
  private[definition] def createInterpreter[T](ec: ExecutionContext, definition: LazyParameter[T]): Context => Future[T] = {
    definition match {
      case e:CompilerLazyParameter[T] => e.prepareEvaluator(this)(ec)
      case _ => throw new IllegalArgumentException(s"LazyParameter $definition is not supported")
    }
  }

  override def syncInterpretationFunction[T](lazyInterpreter: LazyParameter[T]): Context => T = {

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



