package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import cats.data.{OptionT, ValidatedNel}
import cats.instances.future._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.additionalInfo.{NodeAdditionalInfo, NodeAdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{DefinitionContext, ParameterEvaluation, ProcessCompilationError, SingleInputGenericNodeTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.ProcessObjectFactory
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Filter, NodeData}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.definition.UIParameter
import pl.touk.nussknacker.ui.validation.PrettyValidationErrors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/**
 * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
 */
class NodesResources(val processRepository: FetchingProcessRepository[Future],
                     additionalInfoProvider: AdditionalInfoProvider, typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData])(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {


  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    import akka.http.scaladsl.server.Directives._

    pathPrefix("nodes" / Segment) { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
        path("additionalData") {
          entity(as[NodeData]) { nodeData =>

            complete {
              additionalInfoProvider.prepareAdditionalDataForNode(nodeData, process.processingType)
            }
          }
        } ~ path("validation") {
          entity(as[NodeValidationRequest]) { nodeData =>
            complete {
              val modelData = typeToConfig.forTypeUnsafe(process.processingType)
              val globals = modelData.modelData.processDefinition.expressionConfig.globalVariables.mapValues(_.returnType)
              val validationContext = ValidationContext(nodeData.variableTypes, globals, None)
              val baseResult = nodeData.nodeData match {
                case a:Filter => new FilterValidator(modelData.modelData).compile(a, validationContext)
                case a:CustomNode => new CustomNodeValidator(modelData.modelData).compile(a, validationContext)
                case a => DummyValidator.compile(a, validationContext)
              }
              NodeValidationResult(baseResult._1.map(_.map(UIParameter(_, ParameterConfig.empty))), baseResult._2.map(PrettyValidationErrors.formatErrorMessage))
            }
          }
        }
      }
    }
  }
}

object NodeValidationRequest {

  private val objectName = classOf[Object].toString

  //FIXME: this is not enough, we have to make it more similar to: TypeEncoders, at least
  implicit val decodeTypingResults: Decoder[TypingResult] = Decoder.instanceTry { hcursor =>
    hcursor.downField("refClazzName").focus.flatMap(_.asString) match {
      case Some(`objectName`) => Success(Unknown)
      case Some(name) => Try(Typed.typedClass(getClass.getClassLoader.loadClass(name))).orElse(Try(Unknown))
      case None => Success(Unknown)
    }
  }

}

class AdditionalInfoProvider(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData]) {

  //TODO: do not load provider for each request...
  private val providers: ProcessingTypeDataProvider[Option[NodeData => Future[Option[NodeAdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
    .load[NodeAdditionalInfoProvider](pt.modelData.modelClassLoader.classLoader).headOption.map(_.additionalInfo(pt.modelData.processConfig)))

  def prepareAdditionalDataForNode(nodeData: NodeData, processingType: ProcessingType)(implicit ec: ExecutionContext): Future[Option[NodeAdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](providers.forType(processingType).flatten)
      data <- OptionT(provider(nodeData))
    } yield data).value
  }

}

//parameters =>
@JsonCodec(encodeOnly = true) case class NodeValidationResult(parameters: Option[List[UIParameter]], validationErrors: List[NodeValidationError])

@JsonCodec case class NodeValidationRequest(nodeData: NodeData, variableTypes: Map[String, TypingResult])

//EXTRACT CODE BELOW:


trait NodeDataValidator[T<:NodeData] {

  def compile(nodeData: T, validationContext: ValidationContext): (Option[List[Parameter]], List[ProcessCompilationError])

}

class FilterValidator(modelData: ModelData) extends NodeDataValidator[Filter] {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.dictServices.dictRegistry,
      modelData.processDefinition.expressionConfig,
      modelData.processDefinition.settings
    )

  override def compile(nodeData: Filter, validationContext: ValidationContext): (Option[List[Parameter]], List[ProcessCompilationError]) = {
    val validation: ValidatedNel[ProcessCompilationError, _] = expressionCompiler.compile(nodeData.expression, Some(NodeTypingInfo.DefaultExpressionId), validationContext, Typed[Boolean])(NodeId(nodeData.id))
    (Some(List(Parameter[Boolean]("expression"))), validation.fold(_.toList, _ => Nil))
  }
}

class CustomNodeValidator(modelData: ModelData) extends NodeDataValidator[CustomNode] {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.dictServices.dictRegistry,
      modelData.processDefinition.expressionConfig,
      modelData.processDefinition.settings
    )

  private val expressionEvaluator
    = ExpressionEvaluator.withoutLazyVals(GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig), List.empty)

  private val parameterEvaluator = new ProcessObjectFactory(expressionEvaluator)

  override def compile(nodeData: CustomNode, validationContext: ValidationContext): (Option[List[Parameter]], List[ProcessCompilationError]) = {
    val transformer = modelData.processWithObjectsDefinition.customStreamTransformers(nodeData.nodeType)._1.obj
    transformer match {
      case a:SingleInputGenericNodeTransformation[_] =>
        val evaluations = nodeData.parameters.map { parameter =>
            parameter.name -> new ParameterEvaluation {
              override def determine(definition: Parameter): ValidatedNel[ProcessCompilationError, Any] = {
                implicit val nodeId: NodeId = NodeId(nodeData.id)
                //FIXME: pass from above :)
                implicit val meta: MetaData = MetaData("TODO", StreamMetaData())
                expressionCompiler.compile(parameter.expression, Some(parameter.name), validationContext, definition.typ).map { compiled =>
                  parameterEvaluator.prepareParameters(List((TypedParameter(parameter.name, compiled), definition))).values.head
                }
              }
            }
        }.toMap
        val result = a.contextTransformation(Some(DefinitionContext(validationContext, evaluations, Nil)))
        (Some(result.parameters), result.errors)
      case _ =>(None, Nil)
    }
  }
}

object DummyValidator extends NodeDataValidator[NodeData] {
  override def compile(nodeData: NodeData, validationContext: ValidationContext): (Option[List[Parameter]], List[ProcessCompilationError]) = (None, Nil)
}

