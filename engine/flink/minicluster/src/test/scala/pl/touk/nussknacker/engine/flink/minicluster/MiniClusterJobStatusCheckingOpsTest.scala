package pl.touk.nussknacker.engine.flink.minicluster

import org.apache.flink.api.common.JobStatus
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source.SourceReaderContext
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy
import org.apache.flink.connector.datagen.source.{DataGeneratorSource, GeneratorFunction}
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOps._
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOpsTest.GeneratorFunctionStub
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.retry.PatienceConfigToRetryPolicyConverter
import retry.Directly

import java.lang.{Long => JLong}
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class MiniClusterJobStatusCheckingOpsTest
    extends AnyFunSuiteLike
    with PatientScalaFutures
    with BeforeAndAfterAll
    with Matchers {

  private val retry = PatienceConfigToRetryPolicyConverter.toRetryPolicy(patienceConfig)

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("wait for job is finished") {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      env.fromData(List(1).asJava).sinkTo(new DiscardingSink[Int]())
      val jobID = env.execute().getJobID

      flinkMiniClusterWithServices.waitForJobIsFinished(jobID)(retry, retry).futureValue.toTry.get
      flinkMiniClusterWithServices.miniCluster.getJobStatus(jobID).toScala.futureValue shouldBe JobStatus.FINISHED
    }
  }

  test("wait for job is running and cancel it eventually") {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      val dataGenSource = new DataGeneratorSource[Void](
        GeneratorFunctionStub,
        Long.MaxValue,
        RateLimiterStrategy.perSecond(1),
        TypeInformation.of(classOf[Void])
      )

      env
        .fromSource(dataGenSource, WatermarkStrategy.noWatermarks(), "stub-gen")
        .sinkTo(new DiscardingSink[Void]())

      val jobID = env.execute().getJobID

      flinkMiniClusterWithServices
        .withRunningJob(jobID)(retry, retry) {
          Future {
            GeneratorFunctionStub.isOpened shouldBe true
          }
        }
        .futureValue
        .toTry
        .get

      flinkMiniClusterWithServices.miniCluster.getJobStatus(jobID).toScala.futureValue shouldBe JobStatus.CANCELED
    }
  }

  test("should cancel job after waiting for finished ends up with error") {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      env
        .fromSource(sampleGeneratorSource, WatermarkStrategy.noWatermarks(), "sample-gen")
        .sinkTo(new DiscardingSink[Void]())

      val jobID = env.execute().getJobID

      flinkMiniClusterWithServices.waitForJobIsFinished(jobID)(Directly(0), retry).futureValue should matchPattern {
        case Left(_) =>
      }

      flinkMiniClusterWithServices.miniCluster.getJobStatus(jobID).toScala.futureValue shouldBe JobStatus.CANCELED
    }
  }

  test("should ignore cancelling error when job is already finished") {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      env.fromData(List(1).asJava).sinkTo(new DiscardingSink[Int]())
      val jobID = env.execute().getJobID

      eventually {
        flinkMiniClusterWithServices.miniCluster.getJobStatus(jobID).toScala.futureValue shouldBe JobStatus.FINISHED
      }

      flinkMiniClusterWithServices.waitForJobIsFinished(jobID)(retry, retry).futureValue.toTry.get
    }
  }

  private def sampleGeneratorSource = {
    val dataGenSource = new DataGeneratorSource[Void](
      (_: JLong) => null,
      Long.MaxValue,
      RateLimiterStrategy.perSecond(1),
      TypeInformation.of(classOf[Void])
    )
    dataGenSource
  }

}

object MiniClusterJobStatusCheckingOpsTest {

  object GeneratorFunctionStub extends GeneratorFunction[JLong, Void] {

    @volatile var isOpened: Boolean = false

    override def open(readerContext: SourceReaderContext): Unit = {
      super.open(readerContext)
      isOpened = true
    }

    override def map(index: JLong): Void = {
      null
    }

  }

}
