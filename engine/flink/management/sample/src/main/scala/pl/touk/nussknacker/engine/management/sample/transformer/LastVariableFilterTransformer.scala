package pl.touk.nussknacker.engine.management.sample.transformer

import cats.data.Validated.Valid
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.{GenericTransformation, ParameterEvaluation, TransformationResult, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

/* This is example for GenericTransformation, WithExplicitMethodToInvoke will not be here in the future...
   the idea is that we have two parameters:
   - value
   - condition
   And in condition expression we want to have additional variable of type the same as value return type
*/
object LastVariableFilterTransformer extends CustomStreamTransformer with
  WithExplicitMethodToInvoke with GenericTransformation[FlinkCustomStreamTransformation] {

  override def parameterDefinition: List[Parameter] = definition(ValidationContext.empty, Map.empty, Nil).parameters

  //TODO: we'd like to have proper type also here!
  override def returnType: typing.TypingResult = Unknown

  override def runtimeClass: Class[_] = classOf[FlinkCustomStreamTransformation]

  override def additionalDependencies: List[Class[_]] = Nil


  override def invoke(params: List[AnyRef]): AnyRef = implementation(List("value", "condition").zip(params).toMap, Nil)

  override def definition(context: InputContext, params: Map[String, ParameterEvaluation], additionalParams: List[Any]): TransformationResult[FlinkCustomStreamTransformation] = {
    val valueParameter = Parameter("value", Unknown, None, Nil, Map.empty, branchParam = false, isLazyParameter = true, scalaOptionParameter = false, javaOptionalParameter = false)

    val firstEvaluated = params.get("value").map { value =>
      value.determine(valueParameter).map(_.asInstanceOf[LazyParameter[_]].returnType)
    }.getOrElse(Valid(Unknown))

    val additionalVarsForCondition = Map("previous" -> firstEvaluated.getOrElse(Unknown))
    val conditionParameter = Parameter("condition", Unknown, None, Nil, additionalVarsForCondition, branchParam = false, isLazyParameter = true, scalaOptionParameter = false, javaOptionalParameter = false)

    val secondEvaluatedErrors = params.get("condition").map {value =>
      value.determine(conditionParameter).fold(_.toList, _ => Nil)
    }.getOrElse(Nil)

    val errors = firstEvaluated.fold(_.toList, _ => Nil) ++ secondEvaluatedErrors

    //FIXME: context should contain new variable...
    TransformationResult(errors, List(valueParameter, conditionParameter), Nil, context)
  }


  override def implementation(params: Map[String, Any], additionalParams: List[Any]): AnyRef = {
    val value = params("value").asInstanceOf[LazyParameter[Any]]
    val condition = params("condition").asInstanceOf[LazyParameter[Any]]

    //we leave implementation as an exercise for the reader :P
    FlinkCustomStreamTransformation((str: DataStream[Context], flinkCustomNodeContext: FlinkCustomNodeContext) => {
      str.map(ValueWithContext[Any](null, _))
    })
  }



}
