package pl.touk.nussknacker.openapi.enrichers

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{EagerService, NodeId, Params}
import pl.touk.nussknacker.engine.api.component.AllProcessingModesComponent
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  NodeDependency,
  OutputVariableNameDependency,
  Parameter
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.discovery.OpenApiDefinitionDiscovery
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnricherFactory.{
  serviceParam,
  ServiceParamName,
  TransformationState
}
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnricherFactory.TransformationState.{
  FinalState,
  SelectedServiceState
}
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.backend.HttpClientProvider

class SwaggerEnricherFactory(
    config: OpenAPIServicesConfig,
    httpBeProvider: HttpBackendProvider,
    openApiDefinitionDiscovery: OpenApiDefinitionDiscovery,
) extends EagerService
    with SingleInputDynamicComponent[SwaggerEnricher]
    with AllProcessingModesComponent
    with LazyLogging
    with TimeMeasuringService {

  private val fixedParameters = Map[String, () => AnyRef]() // TODO: add configuration

  override protected def serviceName: String = "OpenAPI"

  override type State = TransformationState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    initializeParamsStep(context) orElse
      selectedServiceStep(context) orElse
      finalStep(context, dependencies)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[TransformationState]
  ): SwaggerEnricher = finalState match {
    case Some(FinalState(service, extractor)) =>
      new SwaggerEnricher(
        service,
        extractor,
        config,
        httpBeProvider,
        params,
        getTimeMeasurement = () => timeMeasurement
      )
    case _ => throw new IllegalArgumentException(s"Unexpected state, got: $finalState")
  }

  override def nodeDependencies: List[NodeDependency] =
    List(OutputVariableNameDependency)

  private def initializeParamsStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    val swaggerServices = openApiDefinitionDiscovery.getValidServices(config)
    if (swaggerServices.isEmpty) {
      FinalResults(context, List(CustomNodeError("Cannot find swagger services", paramName = Some(ServiceParamName))))
    } else {
      NextParameters(parameters = serviceParam(swaggerServices) :: Nil, state = Some(TransformationState.InitialState))
    }
  }

  private def selectedServiceStep(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (ServiceParamName, DefinedEagerParameter(serviceName: String, _)) :: Nil,
          Some(TransformationState.InitialState)
        ) =>
      val swaggerServices = openApiDefinitionDiscovery.getValidServices(config)
      swaggerServices.find(_.name.value == serviceName) match {
        case Some(service) =>
          val extractor = new ParametersExtractor(service, fixedParameters)
          NextParameters(extractor.parameterDefinition, Nil, Some(SelectedServiceState(service, extractor)))
        case None =>
          FinalResults(
            context,
            List(
              CustomNodeError(
                s"Cannot find swagger service for name: $serviceName",
                paramName = Some(ServiceParamName)
              )
            )
          )
      }
  }

  private def finalStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(_, Some(SelectedServiceState(service, extractor))) =>
    FinalResults.forValidation(
      context = context,
      errors = List.empty,
      state = Some(TransformationState.FinalState(service, extractor))
    )(ctx =>
      ctx.withVariable(
        name = OutputVariableNameDependency.extract(dependencies),
        value = service.responseSwaggerType.map(_.typingResult).getOrElse(Typed[Unit]),
        paramName = Some(ParameterName(OutputVar.CustomNodeFieldName))
      )
    )
  }

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    httpBeProvider.open(context)
  }

  override def close(): Unit = {
    super.close()
    httpBeProvider.close()
  }

}

object SwaggerEnricherFactory {
  final val ServiceParamName = ParameterName("Service")

  def apply(
      config: OpenAPIServicesConfig,
      openApiDefinitionDiscovery: OpenApiDefinitionDiscovery,
  ) = new SwaggerEnricherFactory(
    config,
    HttpClientProvider.getBackendProvider(config.httpClientConfig),
    openApiDefinitionDiscovery,
  )

  sealed trait TransformationState

  private[enrichers] object TransformationState {
    case object InitialState                                                                 extends TransformationState
    case class SelectedServiceState(service: SwaggerService, extractor: ParametersExtractor) extends TransformationState
    case class FinalState(service: SwaggerService, extractor: ParametersExtractor)           extends TransformationState
  }

  private def serviceParam(services: List[SwaggerService]): Parameter = {
    val values = services.map(_.name.value).sorted.distinct
    fixedValuesWithNullParameter(ServiceParamName, values)
  }

  private[this] def fixedValuesWithNullParameter(parameterName: ParameterName, values: List[String]) = {
    val fixedExprValues = values.map(name => FixedExpressionValue(s"'$name'", name))
    Parameter[String](parameterName).copy(
      editors = List(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue :: fixedExprValues))
    )
  }

}
