package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition, Join}

object FragmentGraphDefinitionExtractor {

  def extractFragmentGraphDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentGraphDefinition] = {
    extractFragmentGraph(fragment).map { case (input, nodes, outputs) =>
      val additionalBranches = fragment.allStartNodes.collect { case a @ FlatNode(_: Join) :: _ =>
        a
      }
      new FragmentGraphDefinition(input.parameters, nodes, additionalBranches, outputs)
    }
  }

  def extractFragmentGraph(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, (FragmentInputDefinition, List[CanonicalNode], List[Output])] = {
    fragment.allStartNodes
      .collectFirst { case FlatNode(input: FragmentInputDefinition) :: nodes =>
        val outputs = collectOutputs(fragment)
        Valid((input, nodes, outputs))
      }
      .getOrElse(Invalid(EmptyFragmentError))
  }

  private def collectOutputs(fragment: CanonicalProcess): List[Output] = {
    fragment.collectAllNodes.collect { case FragmentOutputDefinition(_, name, fields, _) =>
      Output(name, fields.nonEmpty)
    }
  }

}

sealed trait FragmentDefinitionError

case object EmptyFragmentError extends FragmentDefinitionError
