package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.lazyparam.EvaluableLazyParameter
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression

// This class is public for tests purpose. Be aware that its interface can be changed in the future
case class ExpressionLazyParameter[T <: AnyRef](
    nodeId: NodeId,
    parameterDef: definition.Parameter,
    expression: Expression,
    returnType: TypingResult
) extends EvaluableLazyParameter[T] {

  override def prepareEvaluator(
      compilerInterpreter: LazyParameterInterpreter
  ): Context => T = {
    val compilerLazyInterpreter = compilerInterpreter.asInstanceOf[CompilerLazyParameterInterpreter]
    val compiledExpression = compilerLazyInterpreter.deps.expressionCompiler
      .compileWithoutContextValidation(expression, parameterDef.name, parameterDef.typ)(nodeId)
      .valueOr(err =>
        throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}")
      )
    val evaluator = compilerLazyInterpreter.deps.expressionEvaluator
    // TODO: extract BaseCompiledParameter without typingInfo
    val compiledParameter =
      CompiledParameter(TypedExpression(compiledExpression, returnType, typingInfo = null), parameterDef)
    context: Context =>
      evaluator
        .evaluateParameter(compiledParameter, context)(nodeId, compilerLazyInterpreter.metaData)
        .value
        .asInstanceOf[T]
  }

}

trait CompilerLazyParameterInterpreter extends LazyParameterInterpreter {

  def deps: LazyInterpreterDependencies

  def metaData: MetaData

  override def syncInterpretationFunction[T <: AnyRef](lazyParameter: LazyParameter[T]): Context => T = {
    // it's important that it's (...): (Context => T)
    // and not e.g. (...)(Context) => T as we want to be sure when body is evaluated (in particular expression compilation)!
    val evaluator = lazyParameter match {
      case e: EvaluableLazyParameter[T] => e.prepareEvaluator(this)
      case _ => throw new IllegalArgumentException(s"LazyParameter $lazyParameter is not supported")
    }
    v1: Context => evaluator(v1)
  }

}

case class LazyInterpreterDependencies(
    expressionEvaluator: ExpressionEvaluator,
    expressionCompiler: ExpressionCompiler
)
