package pl.touk.nussknacker.engine.compile

import cats.Id
import cats.data.WriterT
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.definition.{Parameter => ParameterDefinition}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList

object NodeParametersAdjuster extends LazyLogging {

  def adjustNonBranchParameters(parameterDefinitions: List[ParameterDefinition], parameters: List[NodeParameter])(
      implicit nodeId: NodeId,
      jobData: JobData
  ): List[NodeParameter] = {
    val nonBranchParametersDefinition = parameterDefinitions.filter(!_.branchParam)
    checkRedundancyAndWarnIfNeeded(nonBranchParametersDefinition, parameters)
    addMissingNodeParameters(nonBranchParametersDefinition, parameters)
  }

  private def checkRedundancyAndWarnIfNeeded(
      parameterDefinitions: List[ParameterDefinition],
      parameters: List[NodeParameter]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): Unit = {
    val redundantParams = parameters.map(_.name).toSet.diff(parameterDefinitions.map(_.name).toSet)
    if (redundantParams.nonEmpty) {
      logger.warn(
        s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
          s"Found redundant parameters: ${redundantParams.toList.map(_.value).sorted.mkString(", ")}. They will be skipped."
      )
    }
  }

  private def addMissingNodeParameters(
      parameterDefinitions: List[ParameterDefinition],
      parameters: List[NodeParameter]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ) = {
    val parameterByName = parameters.map(p => p.name -> p).toMapCheckingDuplicates
    val (missingParameterNames, adjustedParameters) = parameterDefinitions
      .map { parameterDefinition =>
        parameterByName
          .get(parameterDefinition.name)
          .map(parameter => WriterT.value[Id, Set[ParameterName], NodeParameter](parameter))
          .getOrElse {
            WriterT
              .value[Id, Set[ParameterName], NodeParameter](
                NodeParameter(parameterDefinition.name, parameterDefinition.finalDefaultValue)
              )
              .tell(Set(parameterDefinition.name))
          }
      }
      .sequence
      .run
    if (missingParameterNames.nonEmpty) {
      logger.warn(
        s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
          s"Found missing parameters: ${missingParameterNames.toList.map(_.value).sorted.mkString(", ")}. " +
          s"They will be recovered based on the default value from parameters definitions."
      )
    }
    adjustedParameters
  }

}
