package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}

object SampleFragment {

  val fragment = CanonicalProcess(MetaData("fragment1", FragmentSpecificData()),
    List(
      canonicalnode.FlatNode(FragmentInputDefinition("start", List(FragmentParameter("param", FragmentClazzRef[String])))),
      canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))), List.empty)

}
