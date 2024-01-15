package pl.touk.nussknacker.engine.management.sample

import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import pl.touk.nussknacker.engine.api.ServiceLogic.{ParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}

import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}

object LoggingService extends EagerService {

  private val rootLogger = "scenarios"

  @MethodToInvoke(returnType = classOf[Void])
  def invoke(
      @ParamName("logger") @Nullable loggerName: String,
      @ParamName("level") @DefaultValue("T(org.slf4j.event.Level).DEBUG") level: Level,
      @ParamName("message") @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR) message: LazyParameter[String]
  )(implicit metaData: MetaData, nodeId: NodeId): ServiceLogic =
    new ServiceLogic {

      private lazy val logger = LoggerFactory.getLogger(
        (rootLogger :: metaData.name.value :: nodeId.id :: Option(loggerName).toList).filterNot(_.isBlank).mkString(".")
      )

      override def run(
          paramsEvaluator: ParamsEvaluator
      )(implicit runContext: RunContext, executionContext: ExecutionContext): Future[Any] = {
        val message = paramsEvaluator.evaluate().getUnsafe[String]("message")
        level match {
          case Level.TRACE => logger.trace(message)
          case Level.DEBUG => logger.debug(message)
          case Level.INFO  => logger.info(message)
          case Level.WARN  => logger.warn(message)
          case Level.ERROR => logger.error(message)
        }
        Future.successful(())
      }

    }

}
