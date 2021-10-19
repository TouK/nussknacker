package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import cats.Monad
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke}
import pl.touk.nussknacker.engine.baseengine.api.commonTypes._
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes._

import scala.language.higherKinds

//TODO: move to base components
object Union extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(): JoinContextTransformation = {
    ContextTransformation
      .join
      .definedBy { contexts =>
        Valid(computeIntersection(contexts))
      }
      .implementedBy(new JoinCustomBaseEngineComponent {
        override def createTransformation[F[_]:Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): JoinDataBatch => F[ResultType[Result]] =
          (inputs: JoinDataBatch) => continuation(DataBatch(inputs.value.map(_._2)))
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
