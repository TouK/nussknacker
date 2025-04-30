package pl.touk.nussknacker.engine.compile

import cats.data.Validated.valid
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}

object NodeParametersAdjuster extends LazyLogging {

  def adjustNonBranchParameters(parameterDefinitions: List[Parameter], parameters: List[NodeParameter])(
      implicit nodeId: NodeId,
      jobData: JobData
  ): List[NodeParameter] = {
    val definedParamNamesSet = parameterDefinitions.filter(!_.branchParam).map(_.name).toSet
    val usedParamNamesSet    = parameters.map(_.name).toSet

    checkRedundancyAndWarnIfNeeded(definedParamNamesSet, usedParamNamesSet)
    validateMissingness(definedParamNamesSet, usedParamNamesSet)
    // FIXME abr
    parameters
  }

  private def checkRedundancyAndWarnIfNeeded(
      definedParamNamesSet: Set[ParameterName],
      usedParamNamesSet: Set[ParameterName]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): Unit = {
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    if (redundantParams.nonEmpty) {
      logger.warn(
        s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
          s"Found redundant parameters: ${redundantParams.toList.map(_.value).sorted.mkString(", ")}. They will be skipped."
      )
    }
  }

  private def validateMissingness(definedParamNamesSet: Set[ParameterName], usedParamNamesSet: Set[ParameterName])(
      implicit nodeId: NodeId
  ) = {
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    if (notUsedParams.nonEmpty) MissingParameters(notUsedParams).invalidNel[Unit] else valid(())
  }

}
