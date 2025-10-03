package pl.touk.nussknacker.engine.process.exception

import org.apache.flink.api.connector.sink2.{Sink, SinkWriter, WriterInitContext}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.flink.api.exception.{ExceptionHandler, SinkWriterWithExceptionHandler}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.annotation.nowarn

class SinkWriterWithExceptionHandlerTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfter
    with BeforeAndAfterAll
    with VeryPatientScalaFutures {

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  before {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment(env => {
      env.setParallelism(1)

      env
        .fromData[Double](1, 2, 3, 0, 4)
        .sinkTo(new CollectSink)

      ThreadUtils.withContextClassLoader(getClass.getClassLoader) {
        env.execute()
      }

    })
  }

  it should "handle exception" in {
    eventually {
      SinkResultHolder.buffer.results.length mustBe 4
    }
  }

}

class CollectSink extends Sink[Double] {

  override def createWriter(context: WriterInitContext): SinkWriter[Double] =
    new SinkWriter[Double] with SinkWriterWithExceptionHandler[Double] {
      override protected val exceptionHandler: ExceptionHandler = new ExceptionHandler {
        override def handling[T](nodeComponentInfo: Option[NodeComponentInfo], context: Context)(
            action: => T
        ): Option[T] =
          try {
            Some(action)
          } catch {
            case _: Throwable =>
              None
          }
      }

      override def write(element: Double, context: SinkWriter.Context): Unit = {
        val result = exceptionHandler.handling[Double](None, Context.dummy)(
          if (element != 0) (1 / element) else throw new IllegalArgumentException("Dividing by zero")
        )

        result match {
          case Some(reciprocalValue) => SinkResultHolder.buffer.add(reciprocalValue)
          case None                  => ()
        }
      }

      override def flush(endOfInput: Boolean): Unit = ()

      override def close(): Unit = ()

    }

  @nowarn("cat=deprecation")
  def createWriter(context: Sink.InitContext): SinkWriter[Double] =
    throw new UnsupportedOperationException("Not implemented")

}

object SinkResultHolder {
  @transient var buffer = new TestResultsHolder[Double]
}
