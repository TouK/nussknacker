package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression}
import pl.touk.nussknacker.engine.api.lazyparam.{LazyParameterDeps, LazyParameterWithPotentiallyPostponedEvaluator}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.BaseCompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression

final case class PostponedEvaluatorLazyParameter[T <: AnyRef](
    nodeId: NodeId,
    parameterDef: definition.Parameter,
    expression: Expression,
    returnType: TypingResult
) extends LazyParameterWithPotentiallyPostponedEvaluator[T] {

  override def prepareEvaluator(
      lazyParameterDeps: LazyParameterDeps
  ): Context => T = {
    val deps = lazyParameterDeps match {
      case postponedDeps: PostponedEvaluatorLazyParameterDeps => postponedDeps
      case _ => throw new IllegalStateException("Evaluation invoked directly on PostponedEvaluatorLazyParameter")
    }
    val compiledExpression = deps.expressionCompiler
      .compileWithoutContextValidation(expression, parameterDef.name, parameterDef.typ)(nodeId)
      .valueOr(err =>
        throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}")
      )
    val evaluator = deps.expressionEvaluator
    val compiledParameter = {
      new BaseCompiledParameter {
        override def name: String                             = parameterDef.name
        override def expression: CompiledExpression           = compiledExpression
        override def shouldBeWrappedWithScalaOption: Boolean  = parameterDef.scalaOptionParameter
        override def shouldBeWrappedWithJavaOptional: Boolean = parameterDef.javaOptionalParameter
      }
    }
    context: Context =>
      evaluator
        .evaluateParameter(compiledParameter, context)(nodeId, deps.metaData)
        .value
        .asInstanceOf[T]
  }

}

case class PostponedEvaluatorLazyParameterDeps(
    expressionCompiler: ExpressionCompiler,
    expressionEvaluator: ExpressionEvaluator,
    metaData: MetaData
) extends LazyParameterDeps

class DefaultLazyParameterInterpreter(deps: PostponedEvaluatorLazyParameterDeps) extends LazyParameterInterpreter {

  override def syncInterpretationFunction[T <: AnyRef](lazyParameter: LazyParameter[T]): Context => T = {
    // it's important that it's (...): (Context => T)
    // and not e.g. (...)(Context) => T as we want to be sure when body is evaluated (in particular expression compilation)!
    val evaluator = lazyParameter match {
      case e: LazyParameterWithPotentiallyPostponedEvaluator[T] => e.prepareEvaluator(deps)
      case _ => throw new IllegalArgumentException(s"LazyParameter $lazyParameter is not supported")
    }
    v1: Context => evaluator(v1)
  }

}
