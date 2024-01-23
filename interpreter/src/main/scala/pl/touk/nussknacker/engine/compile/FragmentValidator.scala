package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalidNel, valid}
import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.DuplicateFragmentOutputNamesInFragment
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentOutputDefinition

object FragmentValidator {

  def validateUniqueFragmentOutputNames(
      process: CanonicalProcess,
      isFragment: Boolean
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    if (isFragment) {
      val nodes               = process.collectAllNodes
      val fragmentOutputNodes = nodes.collect { case fo: FragmentOutputDefinition => fo }
      val duplicatedOutputNamesWithNodeIds = fragmentOutputNodes
        .groupBy(_.outputName)
        .collect {
          case (name, nodes) if nodes.size > 1 => name -> nodes.map(_.id).toSet
        }
      duplicatedOutputNamesWithNodeIds
        .map(n => invalidNel(DuplicateFragmentOutputNamesInFragment(n._1, n._2)))
        .toList
        .sequence
        .map(_ => ())
    } else {
      valid(())
    }
  }

}
