package pl.touk.esp.engine.process.api

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import pl.touk.esp.engine.flink.api.state.EvictableState
import pl.touk.esp.engine.flink.util.source.StaticSource
import pl.touk.esp.engine.flink.util.source.StaticSource.{Data, Watermark}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EvictableStateTest extends FlatSpec with Matchers with BeforeAndAfter with Eventually {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(1, Seconds)))

  var futureResult: Future[_] = _

  before {
    StaticSource.running = true

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.enableCheckpointing(500)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    env.addSource(StaticSource)
      .keyBy(_ => "staticKey")
      .transform("testOp1", new TestOperator)
      .print()

    futureResult = Future {
      env.execute()
    }
  }

  after {
    StaticSource.running = false
    TestOperator.buffer = List()
    Await.result(futureResult, 5 seconds)
  }

  it should "process state normally when no watermark is generated" in {

    StaticSource.add(Data(1000, "1"))
    StaticSource.add(Data(2000, "2"))
    StaticSource.add(Data(20000, "3"))

    eventually {
      TestOperator.buffer shouldBe List(List("1"), List("1", "2"), List("1", "2", "3"))
    }

  }

  it should "clear state when watermark recevied" in {

    StaticSource.add(Data(1000, "1"))
    StaticSource.add(Watermark(3000))
    StaticSource.add(Data(2000, "2"))
    StaticSource.add(Watermark(8000))
    StaticSource.add(Data(20000, "3"))

    eventually {
      TestOperator.buffer shouldBe List(List("1"), List("1", "2"), List("3"))
    }

  }


}


class TestOperator extends EvictableState[String, String]  {
  override def getState = getRuntimeContext.getState(new ValueStateDescriptor("st1", classOf[List[String]]))

  override def processElement(element: StreamRecord[String]) = {
    setEvictionTimeForCurrentKey(element.getTimestamp + 5000)

    val newState = Option(getState.value()).getOrElse(List()) :+ element.getValue

    TestOperator.buffer = TestOperator.buffer :+ newState
    getState.update(newState)

    output.collect(element)
  }
}

object TestOperator{

  @volatile var buffer = List[List[String]]()

}
