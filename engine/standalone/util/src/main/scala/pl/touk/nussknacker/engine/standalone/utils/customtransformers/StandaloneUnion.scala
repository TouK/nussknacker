package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, MethodToInvoke}
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{CustomTransformerContext, PartInterpreterType}
import pl.touk.nussknacker.engine.standalone.api.StandaloneScenarioEngineTypes._

import scala.concurrent.Future

//TODO: can it be extracted to baseengine?
object StandaloneUnion extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(): JoinContextTransformation = {
    ContextTransformation
      .join
      .definedBy { contexts =>
        Valid(computeIntersection(contexts))
      }
      .implementedBy(new StandaloneJoinCustomTransformer {

        override def createTransformation(continuation: PartInterpreterType[Future], context: CustomTransformerContext): CustomTransformationOutput = {
          (inputs: List[(String, Context)]) =>
            val allContexts = inputs.map(_._2)
            continuation(allContexts)
        }
      })
  }

  private def computeIntersection(contexts: Map[String, ValidationContext]): ValidationContext = {
    contexts.values.toList match {
      case Nil => ValidationContext.empty
      case one :: rest =>
        val commonVariables = one.variables
          .filter(v => rest.forall(_.contains(v._1)))
          .map(v => v._1 -> Typed(v._2 :: rest.map(_ (v._1)): _*))
        one.copy(localVariables = commonVariables)
    }
  }

  override def canHaveManyInputs: Boolean = true

}
