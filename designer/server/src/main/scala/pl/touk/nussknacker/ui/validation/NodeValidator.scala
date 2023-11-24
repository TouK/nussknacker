package pl.touk.nussknacker.ui.validation

import cats.data.OptionT
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, AdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  NodeDataValidator,
  ValidationNotPerformed,
  ValidationPerformed
}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.api.{NodeValidationRequest, NodeValidationResult}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

import scala.concurrent.{ExecutionContext, Future}

class NodeValidator(modelData: ModelData, fragmentRepository: FragmentRepository) {

  private val fragmentResolver = FragmentResolver(k => fragmentRepository.get(k).map(_.canonical))

  private val nodeDataValidator = new NodeDataValidator(modelData, fragmentResolver)

  def validate(scenarioName: ProcessName, nodeData: NodeValidationRequest): NodeValidationResult = {
    implicit val metaData: MetaData = nodeData.processProperties.toMetaData(scenarioName)

    val validationContext = prepareValidationContext(nodeData.variableTypes)
    val branchCtxs        = nodeData.branchVariableTypes.getOrElse(Map.empty).mapValuesNow(prepareValidationContext)

    val edges = nodeData.outgoingEdges.getOrElse(Nil).map(e => OutgoingEdge(e.to, e.edgeType))

    nodeDataValidator.validate(
      nodeData.nodeData,
      validationContext,
      branchCtxs,
      edges
    ) match {
      case ValidationNotPerformed =>
        NodeValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = Nil,
          validationPerformed = false
        )
      case ValidationPerformed(errors, parameters, expressionType) =>
        val uiParams = parameters.map(_.map(UIProcessObjectsFactory.createUIParameter))

        // We don't return MissingParameter error when we are returning those missing parameters to be added - since
        // it's not really exception ATM
        def shouldIgnoreError(pce: ProcessCompilationError): Boolean = pce match {
          case MissingParameters(params, _) => params.forall(missing => uiParams.exists(_.exists(_.name == missing)))
          case _                            => false
        }

        val uiErrors = errors.filterNot(shouldIgnoreError).map(PrettyValidationErrors.formatErrorMessage)
        NodeValidationResult(
          parameters = uiParams,
          expressionType = expressionType,
          validationErrors = uiErrors,
          validationPerformed = true
        )
    }
  }

  private def prepareValidationContext(
      variableTypes: Map[String, TypingResult]
  )(implicit metaData: MetaData): ValidationContext = {
    GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
      .validationContextWithLocalVariables(metaData, variableTypes)
  }

}

class AdditionalInfoProviders(typeToConfig: ProcessingTypeDataProvider[ModelData, _]) {

  // TODO: do not load provider for each request...
  private val nodeProviders: ProcessingTypeDataProvider[Option[NodeData => Future[Option[AdditionalInfo]]], _] =
    typeToConfig.mapValues(pt =>
      ScalaServiceLoader
        .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader)
        .headOption
        .map(_.nodeAdditionalInfo(pt.processConfig))
    )

  private val propertiesProviders: ProcessingTypeDataProvider[Option[MetaData => Future[Option[AdditionalInfo]]], _] =
    typeToConfig.mapValues(pt =>
      ScalaServiceLoader
        .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader)
        .headOption
        .map(_.propertiesAdditionalInfo(pt.processConfig))
    )

  def prepareAdditionalInfoForNode(nodeData: NodeData, processingType: ProcessingType)(
      implicit ec: ExecutionContext
  ): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](nodeProviders.forType(processingType).flatten)
      data     <- OptionT(provider(nodeData))
    } yield data).value
  }

  def prepareAdditionalInfoForProperties(metaData: MetaData, processingType: ProcessingType)(
      implicit ec: ExecutionContext
  ): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](propertiesProviders.forType(processingType).flatten)
      data     <- OptionT(provider(metaData))
    } yield data).value
  }

}
