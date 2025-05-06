package pl.touk.nussknacker.engine.compile

import com.typesafe.scalalogging.Logger
import pl.touk.nussknacker.engine.api.{JobData, NodeId}

object CompilationLoggerExtensions {

  implicit class Ops(logger: Logger) {

    def compilationWarning(message: => String)(implicit nodeId: NodeId, jobData: JobData): Unit = {
      logger.whenWarnEnabled {
        logger.warn(
          s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. $message"
        )
      }
    }

  }

}
