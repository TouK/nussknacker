package pl.touk.nussknacker.engine.definition.fragment

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.DuplicateFragmentOutputNamesInScenario
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter

class FragmentGraphDefinition(
    val fragmentParameters: List[FragmentParameter],
    val nodes: List[CanonicalNode],
    val additionalBranches: List[List[CanonicalNode]],
    allOutputs: List[Output]
) {

  def validOutputs(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Set[Output]] = {
    NonEmptyList.fromList(allOutputs.groupBy(_.name).filter(_._2.size > 1).toList) match {
      case Some(groups) => invalid(groups.map(gr => DuplicateFragmentOutputNamesInScenario(gr._1, nodeId.id)))
      case None         => valid(allOutputs.toSet)
    }
  }

}
