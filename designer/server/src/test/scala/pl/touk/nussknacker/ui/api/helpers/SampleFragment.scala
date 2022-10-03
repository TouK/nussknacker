package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.{SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}

object SampleFragment {

  val fragment = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
    List(
      canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
      canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), List.empty)

}
