package pl.touk.nussknacker.ui.api.utils

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors

object ValidationErrorOps {

  implicit class ValidationErrorOps(val errors: ValidationErrors) extends AnyVal {

    def toHumanReadableMessage(nodeNamesById: Map[NodeId, String]): String = {
      s"Scenario is invalid.${Option(errors.invalidNodes)
          .filterNot(_.isEmpty)
          .map {
            _.map { case (nodeId, nodeErrors) =>
              val nodeLabel = nodeNamesById
                .get(nodeId)
                .map(nodeName => s"$nodeName (id: ${nodeId.value})")
                .getOrElse(nodeId.value)
              s"\n  $nodeLabel: ${nodeErrors.map(_.message).mkString(", ")}"
            }.mkString("\nNode errors:", "", "")
          }
          .getOrElse("")}" +
        s"${Option(errors.globalErrors)
            .filterNot(_.isEmpty)
            .map {
              _.map(_.error.message).mkString("\nGlobal errors: ", ", ", "")
            }
            .getOrElse("")}" +
        s"${Option(errors.processPropertiesErrors)
            .filterNot(_.isEmpty)
            .map {
              _.map(_.message).mkString("\nProperties errors: ", ", ", "")
            }
            .getOrElse("")}"
    }

  }

}
