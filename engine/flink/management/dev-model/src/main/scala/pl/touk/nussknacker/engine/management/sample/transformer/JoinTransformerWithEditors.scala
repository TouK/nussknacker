package pl.touk.nussknacker.engine.management.sample.transformer

import cats.data.Validated.Invalid
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{
  ContextTransformation,
  JoinContextTransformation,
  OutputVar,
  ProcessCompilationError
}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.sample.JavaSampleEnum

import java.time.Duration
import javax.annotation.Nullable

object JoinTransformerWithEditors extends CustomStreamTransformer with Serializable {

  @MethodToInvoke
  def execute(
      @BranchParamName("branchType") branchTypeByBranchId: Map[String, JavaSampleEnum],
      @BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]],
      @BranchParamName("value") @Nullable valueByBranchId: Map[String, LazyParameter[AnyRef]],
      @ParamName("window") window: Duration,
      @OutputVariableName variableName: String
  )(implicit nodeId: NodeId): JoinContextTransformation = {
    ContextTransformation.join
      .definedBy { contexts =>
        val (mainBranches, joinedBranches) = contexts.partition { case (branchId, _) =>
          branchTypeByBranchId(branchId) == JavaSampleEnum.FIRST_VALUE
        }

        if (mainBranches.size != 1 || joinedBranches.size != 1) {
          Invalid(
            ProcessCompilationError
              .CustomNodeError(
                "You must specify exact one main branch and one joined branch",
                Some(ParameterName("branchType"))
              )
          ).toValidatedNel
        } else {
          val mainBranchContext = mainBranches.head._2
          val joinedBranchId    = joinedBranches.head._1

          mainBranchContext.withVariable(OutputVar.customNode(variableName), valueByBranchId(joinedBranchId).returnType)
        }
      }
      .implementedBy { () =>
        ???
      }
  }

}
