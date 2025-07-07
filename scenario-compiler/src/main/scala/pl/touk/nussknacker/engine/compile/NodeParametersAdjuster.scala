package pl.touk.nussknacker.engine.compile

import cats.data.Writer
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.definition.{Parameter => ParameterDefinition}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.CompilationLoggerExtensions.Ops
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}

object NodeParametersAdjuster extends LazyLogging {

  def adjustNonBranchParameters(
      parameterDefinitions: List[ParameterDefinition],
      parameters: Map[ParameterName, NodeParameter]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): List[NodeParameter] = {
    val nonBranchParametersDefinition = parameterDefinitions.filter(!_.branchParam)
    checkRedundancyAndWarnIfNeeded(nonBranchParametersDefinition, parameters)
    addMissingNodeParameters(nonBranchParametersDefinition, parameters)
  }

  private def checkRedundancyAndWarnIfNeeded(
      parameterDefinitions: List[ParameterDefinition],
      parameters: Map[ParameterName, NodeParameter]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): Unit = {
    val redundantParams = parameters.keys.toSet.diff(parameterDefinitions.map(_.name).toSet)
    if (redundantParams.nonEmpty) {
      logger.compilationWarning(
        s"Found redundant parameters: ${redundantParams.toList.map(_.value).sorted.mkString(", ")}. They will be skipped."
      )
    }
  }

  private def addMissingNodeParameters(
      parameterDefinitions: List[ParameterDefinition],
      parameterByName: Map[ParameterName, NodeParameter]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ) = {
    val (missingParameterNames, adjustedParameters) = parameterDefinitions.traverse { parameterDefinition =>
      parameterByName
        .get(parameterDefinition.name)
        .map(parameter => Writer.value[Set[ParameterName], NodeParameter](parameter))
        .getOrElse {
          Writer(
            Set(parameterDefinition.name),
            NodeParameter(parameterDefinition.name, parameterDefinition.finalDefaultValue),
          )
        }
    }.run
    if (missingParameterNames.nonEmpty) {
      logger.compilationWarning(
        s"Found missing parameters: ${missingParameterNames.toList.map(_.value).sorted.mkString(", ")}. " +
          s"They will be recovered based on the default value from parameters definitions."
      )
    }
    adjustedParameters
  }

}
