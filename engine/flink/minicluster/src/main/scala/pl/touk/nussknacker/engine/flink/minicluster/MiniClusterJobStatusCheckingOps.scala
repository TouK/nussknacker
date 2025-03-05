package pl.touk.nussknacker.engine.flink.minicluster

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.executiongraph.{AccessExecutionGraph, AccessExecutionVertex}
import org.apache.flink.runtime.messages.FlinkJobTerminatedWithoutCancellationException
import org.apache.flink.runtime.minicluster.MiniCluster
import retry.Success

import java.util.concurrent.CompletionException
import scala.annotation.tailrec
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

object MiniClusterJobStatusCheckingOps extends LazyLogging {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val InitializingJobStatuses = Set(JobStatus.INITIALIZING, JobStatus.CREATED)

  private val FailingJobStatuses = Set(JobStatus.FAILING, JobStatus.FAILED, JobStatus.RESTARTING)

  implicit def miniClusterWithServicesToOps(miniClusterWithServices: FlinkMiniClusterWithServices): Ops = new Ops(
    miniClusterWithServices.miniCluster
  )

  implicit class Ops(miniCluster: MiniCluster)(implicit ec: ExecutionContext) {

    def waitForJobIsFinished(
        jobID: JobID
    )(
        retryPolicy: retry.Policy,
        terminalCheckRetryPolicyOpt: Option[retry.Policy]
    ): Future[Either[JobStateCheckError, Unit]] = {
      val resultFuture = waitForJobState(jobID, Set(JobStatus.FINISHED), expectedVerticesStatesOpt = None)(retryPolicy)
      finallyCancelJobAndWaitForCancelled(resultFuture, jobID, terminalCheckRetryPolicyOpt)
    }

    def withRunningJob[T](
        jobID: JobID
    )(runningCheckRetryPolicy: retry.Policy, terminalCheckRetryPolicyOpt: Option[retry.Policy])(
        actionToInvokeWithJobRunning: => Future[T]
    ): Future[Either[JobStateCheckError, T]] = {
      val resultFuture = (for {
        _      <- EitherT(waitForJobIsRunningOrFinished(jobID)(runningCheckRetryPolicy))
        result <- EitherT.right(actionToInvokeWithJobRunning)
        _      <- doCheckJobIsNotFailing[JobStateCheckError](jobID)
      } yield result).value
      finallyCancelJobAndWaitForCancelled(resultFuture, jobID, terminalCheckRetryPolicyOpt)
    }

    private def finallyCancelJobAndWaitForCancelled[T](
        resultFuture: Future[Either[JobStateCheckError, T]],
        jobID: JobID,
        terminalCheckRetryPolicyOpt: Option[retry.Policy]
    ): Future[Either[JobStateCheckError, T]] = {
      // It is a kind of asynchronous "finally" block
      resultFuture.transformWith { resultTry =>
        (for {
          _ <- EitherT.right(
            miniCluster.cancelJob(jobID).toScala.recover {
              // It occurs for example when job was already finished when we cancel it
              case ex: CompletionException
                  if ex.getCause.isInstanceOf[FlinkJobTerminatedWithoutCancellationException] =>
            }
          )
          _ <- terminalCheckRetryPolicyOpt
            .map { terminalCheckRetryPolicy =>
              EitherT(waitForJobIsInCancelledOrFinished(jobID)(terminalCheckRetryPolicy))
                .leftMap(JobStateCheckErrorAfterCancel)
            }
            .getOrElse(EitherT.rightT[Future, JobStateCheckError](()))
          result <- EitherT(Future.fromTry(resultTry))
        } yield result).value
      }
    }

    private def waitForJobIsRunningOrFinished(
        jobID: JobID
    )(retryPolicy: retry.Policy): Future[Either[JobStateCheckError, Unit]] = {
      waitForJobState(
        jobID,
        Set(JobStatus.RUNNING, JobStatus.FINISHED),
        expectedVerticesStatesOpt = Some(Set(ExecutionState.RUNNING, ExecutionState.FINISHED))
      )(retryPolicy)
    }

    private def waitForJobIsInCancelledOrFinished(
        jobID: JobID
    )(retryPolicy: retry.Policy): Future[Either[JobStateCheckError, Unit]] = {
      waitForJobState(
        jobID,
        Set(JobStatus.CANCELED, JobStatus.FINISHED),
        expectedVerticesStatesOpt = None
      )(retryPolicy)
    }

    private def waitForJobState(
        jobID: JobID,
        // We have to verify both job state and vertices states because there are cases when one is more useful than another and vice versa:
        // - for checking RUNNING state, it is better to check vertices states because Flink reports job as RUNNING even if some of the vertices are only scheduled (not running yet)
        // - for checking FINISHED state, it is better to check job states because Flink reports vertices as FINISHED even if job is not FINISHED yet
        expectedJobStatuses: Set[JobStatus],
        expectedVerticesStatesOpt: Option[Set[ExecutionState]],
    )(
        retryPolicy: retry.Policy
    ): Future[Either[JobStateCheckError, Unit]] =
      retryPolicy {
        (for {
          // we have to verify if job is initialized, because otherwise, not all vertices are available so vertices status check would be misleading
          executionGraph <- EitherT.right(miniCluster.getExecutionGraph(jobID).toScala): EitherT[
            Future,
            JobStateCheckError,
            AccessExecutionGraph
          ]
          _ = {
            logger.trace(
              s"Job [id=${executionGraph.getJobID}, name=${executionGraph.getJobName}] state: ${executionGraph.getState}, vertices states: ${executionGraph.getAllExecutionVertices.asScala
                  .map(_.getExecutionState)}"
            )
          }
          _ <- EitherT.cond[Future](
            expectedJobStatuses.intersect(InitializingJobStatuses).nonEmpty || !InitializingJobStatuses.contains(
              executionGraph.getState
            ),
            (),
            JobIsNotInitializedError(jobID, executionGraph.getJobName)
          )
          // we check failing status separately to provide more detailed information about job state in error case
          _ <-
            if (expectedJobStatuses.intersect(FailingJobStatuses).isEmpty)
              doCheckJobIsNotFailing(executionGraph)
            else
              EitherT.rightT[Future, JobStateCheckError](())
          _ <- EitherT.cond[Future][JobStateCheckError, Unit](
            expectedJobStatuses.contains(executionGraph.getState),
            (),
            JobInUnexpectedStateError(jobID, executionGraph.getJobName, executionGraph.getState, expectedJobStatuses)
          )
          // this check have to be after job state checks because we don't want to break fail-fast behavior - see jobStateCheckResultSuccess
          _ <- expectedVerticesStatesOpt
            .map(checkVerticesStates(executionGraph, _))
            .getOrElse(EitherT.rightT[Future, JobStateCheckError](()))
        } yield ()).value
      }(jobStateCheckResultSuccess, ec)

    def checkJobIsNotFailing(jobID: JobID): Future[Either[JobIsFailingError, Unit]] =
      doCheckJobIsNotFailing[JobIsFailingError](jobID).value

    private def doCheckJobIsNotFailing[E >: JobIsFailingError](jobID: JobID): EitherT[Future, E, Unit] =
      for {
        executionGraph <- EitherT.right(miniCluster.getExecutionGraph(jobID).toScala)
        _              <- doCheckJobIsNotFailing[E](executionGraph)
      } yield ()

    private def doCheckJobIsNotFailing[E >: JobIsFailingError](
        executionGraph: AccessExecutionGraph
    ): EitherT[Future, E, Unit] = {
      EitherT.cond[Future][E, Unit](
        !FailingJobStatuses.contains(executionGraph.getState),
        (),
        JobIsFailingError(executionGraph)
      )
    }

    private def checkVerticesStates(
        executionGraph: AccessExecutionGraph,
        expectedVerticesStates: Set[ExecutionState]
    ): EitherT[Future, JobStateCheckError, Unit] = {
      val executionVertices = executionGraph.getAllExecutionVertices.asScala
      val verticesNotInExpectedState =
        executionVertices.filterNot(v => expectedVerticesStates.contains(v.getExecutionState))
      EitherT.cond[Future][JobStateCheckError, Unit](
        verticesNotInExpectedState.isEmpty,
        (),
        JobVerticesInUnexpectedStateError(
          executionGraph.getJobID,
          executionGraph.getJobName,
          verticesNotInExpectedState,
          expectedVerticesStates
        )
      )
    }

    private val jobStateCheckResultSuccess = Success[Either[JobStateCheckError, Unit]] {
      case Right(_)    => true
      case Left(error) => !shouldRepeat(error)
    }

    @tailrec
    private def shouldRepeat(err: JobStateCheckError): Boolean = err match {
      case _: JobIsNotInitializedError => true
      // unexpected state which is globally terminal means that it won't be better so there is no sense in next attempts
      case JobIsFailingError(executionGraph)             => !executionGraph.getState.isGloballyTerminalState
      case JobInUnexpectedStateError(_, _, jobStatus, _) => !jobStatus.isGloballyTerminalState
      case _: JobVerticesInUnexpectedStateError          => true
      case JobStateCheckErrorAfterCancel(cause)          => shouldRepeat(cause)
    }

  }

  sealed abstract class JobStateCheckError(msg: String, cause: JobStateCheckError) extends Exception(msg, cause) {
    def this(msg: String) = this(msg, null)

    def jobID: JobID
    def jobName: String
  }

  case class JobIsNotInitializedError(override val jobID: JobID, override val jobName: String)
      extends JobStateCheckError(s"Job [id=$jobID, name=$jobName] is not initialized")

  case class JobIsFailingError(executionGraph: AccessExecutionGraph)
      extends JobStateCheckError(
        s"Job [id=${executionGraph.getJobID}, name=${executionGraph.getJobName}] is in ${executionGraph.getState} state. " +
          s"Failure info: ${Option(executionGraph.getFailureInfo).map(_.getExceptionAsString).orNull}"
      ) {

    override def jobID: JobID = executionGraph.getJobID

    override def jobName: String = executionGraph.getJobName
  }

  case class JobInUnexpectedStateError(
      jobID: JobID,
      jobName: String,
      jobStatus: JobStatus,
      expectedStatuses: Set[JobStatus]
  ) extends JobStateCheckError(
        s"Job [id=$jobID, name=$jobName] is not in expected (${expectedStatuses.mkString(" or ")}) state: $jobStatus"
      )

  case class JobVerticesInUnexpectedStateError(
      override val jobID: JobID,
      override val jobName: String,
      verticesNotInExpectedState: Iterable[AccessExecutionVertex],
      expectedVerticesStates: Set[ExecutionState]
  ) extends JobStateCheckError(
        verticesNotInExpectedState
          .map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")
          .mkString(
            s"Some vertices of job [id=$jobID, name=$jobName] are not in expected (${expectedVerticesStates.mkString(" or ")}) state: ",
            ", ",
            ""
          )
      )

  case class JobStateCheckErrorAfterCancel(cause: JobStateCheckError)
      extends JobStateCheckError(
        s"Job [id=${cause.jobID}, name=${cause.jobName}] has unexpected state after cancelling it",
        cause
      ) {

    override def jobID: JobID = cause.jobID

    override def jobName: String = cause.jobName
  }

}
