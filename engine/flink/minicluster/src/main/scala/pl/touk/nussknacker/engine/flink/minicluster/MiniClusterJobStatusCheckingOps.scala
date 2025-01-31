package pl.touk.nussknacker.engine.flink.minicluster

import cats.data.EitherT
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.executiongraph.{AccessExecutionGraph, AccessExecutionVertex}
import org.apache.flink.runtime.messages.FlinkJobTerminatedWithoutCancellationException
import org.apache.flink.runtime.minicluster.MiniCluster

import java.util.concurrent.CompletionException
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object MiniClusterJobStatusCheckingOps {

  implicit class Ops(miniCluster: MiniCluster)(implicit ec: ExecutionContext) {

    def waitForFinished(
        jobID: JobID
    )(retryPolicy: retry.Policy): Future[Either[JobVerticesStatesCheckError, Unit]] = {
      waitForJobVerticesStates(jobID, checkIfNotFailing = true, Set(ExecutionState.FINISHED))(retryPolicy)
    }

    def withJobRunning[T](jobID: JobID, retryPolicy: retry.Policy)(
        actionToInvokeWithJobRunning: => Future[T]
    ): Future[Either[JobVerticesStatesCheckError, T]] = {
      val resultFuture = (for {
        _      <- EitherT(waitForRunningOrFinished(jobID)(retryPolicy))
        result <- EitherT.right(actionToInvokeWithJobRunning)
        _      <- EitherT(assertJobNotFailing[JobVerticesStatesCheckError](jobID))
      } yield result).value
      // It is a kind of asynchronous "finally" block
      resultFuture.transformWith { resultTry =>
        for {
          _ <- miniCluster.cancelJob(jobID).toScala.recover {
            // It occurs for example when job was already finished when we cancel it
            case ex: CompletionException if ex.getCause.isInstanceOf[FlinkJobTerminatedWithoutCancellationException] =>
          }
          _      <- waitForAnyTerminalState(jobID)(retryPolicy)
          result <- Future.fromTry(resultTry)
        } yield result
      }
    }

    private def waitForRunningOrFinished(
        jobID: JobID
    )(retryPolicy: retry.Policy): Future[Either[JobVerticesStatesCheckError, Unit]] = {
      waitForJobVerticesStates(
        jobID,
        checkIfNotFailing = true,
        Set(ExecutionState.RUNNING, ExecutionState.FINISHED)
      )(retryPolicy)
    }

    private def waitForAnyTerminalState(
        jobID: JobID
    )(retryPolicy: retry.Policy): Future[Either[JobVerticesStatesCheckError, Unit]] = {
      waitForJobVerticesStates(
        jobID,
        checkIfNotFailing = false,
        Set(ExecutionState.CANCELED, ExecutionState.FINISHED, ExecutionState.FAILED)
      )(retryPolicy)
    }

    private def waitForJobVerticesStates(
        jobID: JobID,
        checkIfNotFailing: Boolean,
        // We check vertices states instead of job status because of two reasons:
        // 1. Flink reports job as RUNNING even if some of the vertices are only scheduled (not running yet)
        // 2. We want to more precisely return info about not matching vertices states
        expectedVerticesStates: Set[ExecutionState]
    )(
        retryPolicy: retry.Policy
    ): Future[Either[JobVerticesStatesCheckError, Unit]] =
      retryPolicy {
        (for {
          // we have to verify if job is initialized, because otherwise, not all vertices are available so vertices status check would be misleading
          executionGraph <- EitherT.right(miniCluster.getExecutionGraph(jobID).toScala): EitherT[
            Future,
            JobVerticesStatesCheckError,
            AccessExecutionGraph
          ]
          _ <- EitherT.cond[Future](
            executionGraph.getState != JobStatus.INITIALIZING,
            (),
            JobIsNotInitializedError(jobID, executionGraph.getJobName)
          )
          executionVertices = executionGraph.getAllExecutionVertices.asScala
          verticesNotInExpectedState = executionVertices.filterNot(v =>
            expectedVerticesStates.contains(v.getExecutionState)
          )
          _ <- EitherT.cond[Future][JobVerticesStatesCheckError, Unit](
            verticesNotInExpectedState.isEmpty,
            (),
            JobVerticesNotInExpectedStateError(
              executionGraph.getJobID,
              executionGraph.getJobName,
              verticesNotInExpectedState,
              expectedVerticesStates
            )
          )
        } yield ()).value
      }

    def assertJobNotFailing[E >: JobIsFailingError](jobID: JobID): Future[Either[E, Unit]] =
      (for {
        executionGraph <- EitherT.right(miniCluster.getExecutionGraph(jobID).toScala)
        _              <- assertJobNotFailing[E](executionGraph)
      } yield ()).value

    private def assertJobNotFailing[E >: JobIsFailingError](
        executionGraph: AccessExecutionGraph
    ): EitherT[Future, E, Unit] = {
      EitherT.cond[Future][E, Unit](
        !Set(JobStatus.FAILING, JobStatus.FAILED, JobStatus.RESTARTING).contains(executionGraph.getState),
        (),
        JobIsFailingError(executionGraph)
      )
    }

  }

  sealed abstract class JobVerticesStatesCheckError(msg: String) extends Exception(msg)

  case class JobIsNotInitializedError(jobID: JobID, jobName: String)
      extends JobVerticesStatesCheckError(s"Job [id=$jobID, name=$jobName] is not initialized")

  case class JobIsFailingError(executionGraph: AccessExecutionGraph)
      extends JobVerticesStatesCheckError(
        s"Job [id=${executionGraph.getJobID}, name=${executionGraph.getJobName}] is in failing state. Failure info: ${Option(executionGraph.getFailureInfo).map(_.getExceptionAsString).orNull}"
      )

  case class JobVerticesNotInExpectedStateError(
      jobID: JobID,
      jobName: String,
      verticesNotInExpectedState: Iterable[AccessExecutionVertex],
      expectedVerticesStates: Set[ExecutionState]
  ) extends JobVerticesStatesCheckError(
        verticesNotInExpectedState
          .map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")
          .mkString(
            s"Some vertices of ob [id=$jobID, name=$jobName] are not in expected (${expectedVerticesStates.mkString(", ")}) state): ",
            ", ",
            ""
          )
      )

}
