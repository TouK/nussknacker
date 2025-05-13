package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.LazyParameter._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.BaseCompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression

// This class looks like a LazyParameter but actually it's not - it's a creator of the LazyParameter.
// It's Flink specific. And should not be evaluated directly. See the `evaluate` method implementation and
// DefaultToEvaluateFunctionConverter class.
final class EvaluableLazyParameterCreator[T <: AnyRef](
    nodeId: NodeId,
    parameterDef: definition.Parameter,
    expression: Expression,
    override val returnType: TypingResult
) extends CustomLazyParameter[T] {

  def create(deps: EvaluableLazyParameterCreatorDeps): EvaluableLazyParameter[T] = {
    createEvaluableLazyParameter(deps)
  }

  override def evaluate: Evaluate[T] = { _ =>
    throw new IllegalStateException(
      s"[${classOf[EvaluableLazyParameterCreator[_]].getName}] looks like a LazyParameter, but actually it's a creator of [${classOf[EvaluableLazyParameter[T]].getName}] instance. It cannot be evaluated directly!"
    )
  }

  private def createEvaluableLazyParameter(deps: EvaluableLazyParameterCreatorDeps) = {
    val compiledExpression = deps.expressionCompiler
      .compileWithoutContextValidation(expression, parameterDef.name, parameterDef.typ)(nodeId)
      .valueOr(err =>
        throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}")
      )
    val compiledParameter: BaseCompiledParameter = new BaseCompiledParameter {
      override val name: ParameterName                      = parameterDef.name
      override val expression: CompiledExpression           = compiledExpression
      override val shouldBeWrappedWithScalaOption: Boolean  = parameterDef.scalaOptionParameter
      override val shouldBeWrappedWithJavaOptional: Boolean = parameterDef.javaOptionalParameter
    }
    new EvaluableLazyParameter[T](
      compiledParameter,
      deps.expressionEvaluator,
      nodeId,
      deps.jobData,
      returnType
    )
  }

}

class EvaluableLazyParameterCreatorDeps(
    val expressionCompiler: ExpressionCompiler,
    val expressionEvaluator: ExpressionEvaluator,
    val jobData: JobData
)

class DefaultToEvaluateFunctionConverter(deps: EvaluableLazyParameterCreatorDeps) extends ToEvaluateFunctionConverter {

  override def toEvaluateFunction[T <: AnyRef](lazyParameter: LazyParameter[T]): Evaluate[T] = {
    // it's important that it's (...): (Context => T)
    // and not e.g. (...)(Context) => T as we want to be sure when body is evaluated (in particular expression compilation)!
    prepareLazyParameterToBeEvaluated(lazyParameter).evaluate
  }

  private def prepareLazyParameterToBeEvaluated[T <: AnyRef](lazyParameter: LazyParameter[T]): LazyParameter[T] = {
    lazyParameter match {
      case creator: EvaluableLazyParameterCreator[T] =>
        prepareLazyParameterToBeEvaluated(creator.create(deps))
      case p: EvaluableLazyParameter[T] => p
      case p: FixedLazyParameter[T]     => p
      case p: ProductLazyParameter[_, _] =>
        LazyParameter
          .product(
            prepareLazyParameterToBeEvaluated(p.arg1),
            prepareLazyParameterToBeEvaluated(p.arg2)
          )
          .asInstanceOf[LazyParameter[T]]
      case p: SequenceLazyParameter[_, T] =>
        LazyParameter.sequence(
          fa = p.args.map(prepareLazyParameterToBeEvaluated),
          wrapResult = p.wrapResult,
          wrapReturnType = p.wrapReturnType
        )
      case p: MappedLazyParameter[_, T] =>
        LazyParameter.mapped(
          prepareLazyParameterToBeEvaluated(p.arg),
          p.fun,
          p.transformTypingResult
        )
      case p: CustomLazyParameter[T] => p
    }
  }

}
