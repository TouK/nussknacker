package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, MethodToInvoke}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.standalone.api.JoinStandaloneCustomTransformer

object StandaloneUnion extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(): JoinContextTransformation = {
    ContextTransformation
      .join
      .definedBy { contexts =>
        Valid(computeIntersection(contexts))
      }
      .implementedBy(new JoinStandaloneCustomTransformer {
        override def createTransformation(outputVariable: Option[String]): StandaloneCustomTransformation = {
          (outputContinuation, _) =>
            (inputPartsMap: Map[String, List[Context]], ec) =>
              println(inputPartsMap)
              val allContexts = inputPartsMap.flatMap(_._2).toList
              outputContinuation(allContexts, ec)
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

}
