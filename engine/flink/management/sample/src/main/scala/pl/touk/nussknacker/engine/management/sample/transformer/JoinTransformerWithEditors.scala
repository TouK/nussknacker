package pl.touk.nussknacker.engine.management.sample.transformer

import java.time.Duration

import cats.data.Validated.Invalid
import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{FatalUnknownError, NodeId}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation}
import pl.touk.nussknacker.engine.api._
import pl.touk.sample.JavaSampleEnum

object JoinTransformerWithEditors extends CustomStreamTransformer with Serializable {

  override def canHaveManyInputs: Boolean = true

  @MethodToInvoke
  def execute(@BranchParamName("branchType") branchTypeByBranchId: Map[String, JavaSampleEnum],
              @BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]],
              @BranchParamName("value") @Nullable valueByBranchId: Map[String, LazyParameter[Any]],
              @ParamName("window") window: Duration,
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation = {
    ContextTransformation
      .join.definedBy { contexts =>
      val (mainBranches, joinedBranches) = contexts.partition {
        case (branchId, _) => branchTypeByBranchId(branchId) == JavaSampleEnum.FIRST_VALUE
      }

      if (mainBranches.size != 1 || joinedBranches.size != 1) {
        Invalid(FatalUnknownError("You must specify exact one main branch and one joined branch")).toValidatedNel
      } else {
        val mainBranchContext = mainBranches.head._2
        val joinedBranchId = joinedBranches.head._1

        mainBranchContext.withVariable(variableName, valueByBranchId(joinedBranchId).returnType)
      }
    }.implementedBy {
      () => ???
    }
  }

}
