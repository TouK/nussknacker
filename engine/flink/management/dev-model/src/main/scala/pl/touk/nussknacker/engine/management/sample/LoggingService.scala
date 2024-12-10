package pl.touk.nussknacker.engine.management.sample

import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}

object LoggingService extends EagerService {

  private val rootLogger = "scenarios"

  @MethodToInvoke(returnType = classOf[Void])
  def prepare(
      @ParamName("logger") @Nullable loggerName: String,
      @ParamName("level") @DefaultValue("T(org.slf4j.event.Level).DEBUG") level: Level,
      @ParamName("message") @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR) message: LazyParameter[String]
  )(implicit metaData: MetaData, nodeId: NodeId): ServiceInvoker =
    new ServiceInvoker {

      private lazy val logger = LoggerFactory.getLogger(
        (rootLogger :: metaData.name.value :: nodeId.id :: Option(loggerName).toList).filterNot(_.isBlank).mkString(".")
      )

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: ServiceInvocationCollector,
          componentUseCase: ComponentUseCase
      ): Future[Any] = {
        val msg = message.evaluate(context)
        level match {
          case Level.TRACE => logger.trace(msg)
          case Level.DEBUG => logger.debug(msg)
          case Level.INFO  => logger.info(msg)
          case Level.WARN  => logger.warn(msg)
          case Level.ERROR => logger.error(msg)
        }
        Future.successful(())
      }

    }

}
