package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression}
import pl.touk.nussknacker.engine.api.lazyparam.{EvaluableLazyParameter, LazyParameterDeps}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.{BaseCompiledParameter, CompiledParameter}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression

// This class is public for tests purpose. Be aware that its interface can be changed in the future
// TODO: rename to LazyParameterWithPostponedEvaluator
case class ExpressionLazyParameter[T <: AnyRef](
    nodeId: NodeId,
    parameterDef: definition.Parameter,
    expression: Expression,
    returnType: TypingResult
) extends EvaluableLazyParameter[T] {

  override def prepareEvaluator(
      deps: LazyParameterDeps
  ): Context => T = {
    val lazyParameterDeps = deps match {
      case postponedDeps: SerializableLazyParameterDeps => postponedDeps
      case _ => throw new IllegalStateException("Evaluation invoked directly on LazyParameterWithPostponedEvaluator")
    }
    val compiledExpression = lazyParameterDeps.expressionCompiler
      .compileWithoutContextValidation(expression, parameterDef.name, parameterDef.typ)(nodeId)
      .valueOr(err =>
        throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}")
      )
    val evaluator = lazyParameterDeps.expressionEvaluator
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
        .evaluateParameter(compiledParameter, context)(nodeId, lazyParameterDeps.metaData)
        .value
        .asInstanceOf[T]
  }

}

// TODO: Rename to just ExpressionLazyParameter
class InstantExpressionLazyParameter[T <: AnyRef](
    compiledParameter: CompiledParameter,
    evaluator: ExpressionEvaluator,
    nodeId: NodeId,
    metaData: MetaData
) extends EvaluableLazyParameter[T] {

  override def prepareEvaluator(deps: LazyParameterDeps): Context => T = { context: Context =>
    evaluator
      .evaluateParameter(compiledParameter, context)(nodeId, metaData)
      .value
      .asInstanceOf[T]
  }

  override def returnType: TypingResult = compiledParameter.typingInfo.typingResult

}

class CompilerLazyParameterInterpreter(deps: SerializableLazyParameterDeps) extends LazyParameterInterpreter {

  override def syncInterpretationFunction[T <: AnyRef](lazyParameter: LazyParameter[T]): Context => T = {
    // it's important that it's (...): (Context => T)
    // and not e.g. (...)(Context) => T as we want to be sure when body is evaluated (in particular expression compilation)!
    val evaluator = lazyParameter match {
      case e: EvaluableLazyParameter[T] => e.prepareEvaluator(deps)
      case _ => throw new IllegalArgumentException(s"LazyParameter $lazyParameter is not supported")
    }
    v1: Context => evaluator(v1)
  }

}

case class SerializableLazyParameterDeps(
    expressionCompiler: ExpressionCompiler,
    expressionEvaluator: ExpressionEvaluator,
    metaData: MetaData
) extends LazyParameterDeps
