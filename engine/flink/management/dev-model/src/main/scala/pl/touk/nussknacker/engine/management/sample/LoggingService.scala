package pl.touk.nussknacker.engine.management.sample

import cats.implicits.catsSyntaxOptionId
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.BoolParameterEditor
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}

object LoggingService extends EagerService {

  private val rootLogger = "scenarios"

  @MethodToInvoke(returnType = classOf[Void])
  def prepare(
      @ParamName("logger") @Nullable loggerName: String,
      @ParamName("level") @DefaultValue("T(org.slf4j.event.Level).DEBUG") level: Level,
      @ParamName("message") @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR) message: LazyParameter[
        TemplateEvaluationResult
      ]
  )(implicit metaData: MetaData, nodeId: NodeId): ServiceInvoker =
    new ServiceInvoker with WithActionParametersSupport {
      private val debuggingWithLoggingComponentsAllowedPropertyName = "debuggingWithLoggingComponentsAllowed"
      private lazy val logger = LoggerFactory.getLogger(
        (rootLogger :: metaData.name.value :: nodeId.id :: Option(loggerName).toList).filterNot(_.isBlank).mkString(".")
      )

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: ServiceInvocationCollector,
          componentUseContext: ComponentUseContext,
      ): Future[Any] = {
        if (isLoggingAllowed(componentUseContext)) {
          val msg = message.evaluate(context).renderedTemplate
          level match {
            case Level.TRACE => logger.trace(msg)
            case Level.DEBUG => logger.debug(msg)
            case Level.INFO  => logger.info(msg)
            case Level.WARN  => logger.warn(msg)
            case Level.ERROR => logger.error(msg)
          }
        }
        Future.successful(())
      }

      private def isLoggingAllowed(componentUseContext: ComponentUseContext) =
        componentUseContext
          .deploymentData()
          .flatMap(_.get(debuggingWithLoggingComponentsAllowedPropertyName))
          .exists(_.toBoolean)

      override def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, ParameterConfig]] = Map(
        ScenarioActionName.Deploy -> Map(
          ParameterName(debuggingWithLoggingComponentsAllowedPropertyName) -> ParameterConfig(
            defaultValue = "false".some,
            editor = BoolParameterEditor.some,
            validators = None,
            label = "Enable debugging with logging components".some,
            hintText = None
          )
        )
      )

    }

}
