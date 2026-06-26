package pl.touk.nussknacker.engine.flink.api.operator

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.{StateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.{BasicTypeInfo, TypeInformation}
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.runtime.state.VoidNamespace
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.operators.{InternalTimerService, Output}
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness
import org.apache.flink.util.Collector
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class RestorableAndFlushingOperatorTest extends AnyFunSuite with Matchers with BeforeAndAfterEach {

  private val delayMillis = 100L
  private val shiftMillis = 1000L

  private val keySelector: KeySelector[Integer, String] = (value: Integer) => s"k$value"
  private val keyType: TypeInformation[String]          = BasicTypeInfo.STRING_TYPE_INFO

  override def beforeEach(): Unit = {
    BufferingTestFunction.flushedKeys.clear()
    BufferingTestFunction.restoredKeys.clear()
  }

  private def newHarness(
      operator: org.apache.flink.streaming.api.operators.OneInputStreamOperator[Integer, Integer]
  ): KeyedOneInputStreamOperatorTestHarness[String, Integer, Integer] =
    new KeyedOneInputStreamOperatorTestHarness[String, Integer, Integer](operator, keySelector, keyType)

  private def emittedValues(
      harness: KeyedOneInputStreamOperatorTestHarness[String, Integer, Integer]
  ): List[Int] =
    harness.getOutput.asScala.toList.collect { case record: StreamRecord[Integer @unchecked] =>
      record.getValue.intValue()
    }

  test("OneInputFlushingKeyedOperator flushes every key's pending events on end of input") {
    val operator = new OneInputFlushingKeyedOperator[String, Integer, Integer](new BufferingTestFunction(shiftMillis))
    val harness  = newHarness(operator)
    harness.open()
    harness.setProcessingTime(0L)

    // both events are queued with a timer at delayMillis, which we never reach
    harness.processElement(10, 0L)
    harness.processElement(20, 0L)
    emittedValues(harness) shouldBe empty

    operator.endInput()

    emittedValues(harness) should contain theSameElementsAs List(10, 20)
    BufferingTestFunction.flushedKeys.keySet().asScala should contain theSameElementsAs List("k10", "k20")

    harness.close()
  }

  test("RestorableKeyedOperator re-paces every key's timer by a constant after a restore") {
    val snapshot = {
      val operator = new RestorableKeyedOperator[String, Integer, Integer](new BufferingTestFunction(shiftMillis))
      val harness  = newHarness(operator)
      harness.open()
      harness.setProcessingTime(0L)
      harness.processElement(10, 0L)
      harness.processElement(20, 0L)
      val state = harness.snapshot(0L, 0L)
      harness.close()
      state
    }

    val operator = new RestorableKeyedOperator[String, Integer, Integer](new BufferingTestFunction(shiftMillis))
    val harness  = newHarness(operator)
    harness.setup()
    harness.initializeState(snapshot)
    harness.open() // triggers re-pacing: each key's timer is deleted and re-registered at delayMillis + shiftMillis

    // restoreForCurrentKey was driven once per key by the operator
    BufferingTestFunction.restoredKeys.keySet().asScala should contain theSameElementsAs List("k10", "k20")

    // at the ORIGINAL fire time nothing is emitted, proving the original timers were re-paced (not just restored)
    harness.setProcessingTime(delayMillis)
    emittedValues(harness) shouldBe empty

    // at the SHIFTED fire time both keys fire
    harness.setProcessingTime(delayMillis + shiftMillis)
    emittedValues(harness) should contain theSameElementsAs List(10, 20)

    harness.close()
  }

  test("RestorableOneInputFlushingKeyedOperator re-paces timers after a restore and still flushes on end of input") {
    val snapshot = {
      val operator =
        new RestorableOneInputFlushingKeyedOperator[String, Integer, Integer](new BufferingTestFunction(shiftMillis))
      val harness = newHarness(operator)
      harness.open()
      harness.setProcessingTime(0L)
      harness.processElement(10, 0L)
      harness.processElement(20, 0L)
      val state = harness.snapshot(0L, 0L)
      harness.close()
      state
    }

    val operator =
      new RestorableOneInputFlushingKeyedOperator[String, Integer, Integer](new BufferingTestFunction(shiftMillis))
    val harness = newHarness(operator)
    harness.setup()
    harness.initializeState(snapshot)
    harness.open()

    // restoreForCurrentKey was driven once per key through the composition
    BufferingTestFunction.restoredKeys.keySet().asScala should contain theSameElementsAs List("k10", "k20")

    // re-paced: nothing at the original time
    harness.setProcessingTime(delayMillis)
    emittedValues(harness) shouldBe empty

    // flushing still works through the composition: end input emits the queued values without waiting for the timers
    operator.endInput()
    emittedValues(harness) should contain theSameElementsAs List(10, 20)
    BufferingTestFunction.flushedKeys.keySet().asScala should contain theSameElementsAs List("k10", "k20")

    harness.close()
  }

}

object BufferingTestFunction {
  // Per-key records of the keys each callback was invoked for: they let the tests assert the operator drove the
  // callback once per key, not just that the net effect happened.
  val flushedKeys: ConcurrentHashMap[String, Integer]  = new ConcurrentHashMap[String, Integer]()
  val restoredKeys: ConcurrentHashMap[String, Integer] = new ConcurrentHashMap[String, Integer]()
}

private class BufferingTestFunction(shiftMillis: Long)
    extends KeyedProcessFunction[String, Integer, Integer]
    with KeyedRestoreFunction[String, Integer]
    with KeyedFlushFunction[String, Integer] {

  private val delayMillis = 100L

  private val pendingValueDescriptor = new ValueStateDescriptor[Integer]("pendingValue", classOf[Integer])
  private val fireTimeDescriptor =
    new ValueStateDescriptor[java.lang.Long]("fireTime", classOf[java.lang.Long])

  @transient private var pendingValue: ValueState[Integer]    = _
  @transient private var fireTime: ValueState[java.lang.Long] = _

  override def open(openContext: OpenContext): Unit = {
    pendingValue = getRuntimeContext.getState(pendingValueDescriptor)
    fireTime = getRuntimeContext.getState(fireTimeDescriptor)
  }

  override def processElement(
      value: Integer,
      ctx: KeyedProcessFunction[String, Integer, Integer]#Context,
      out: Collector[Integer]
  ): Unit = {
    val time = ctx.timerService().currentProcessingTime() + delayMillis
    pendingValue.update(value)
    fireTime.update(time)
    ctx.timerService().registerProcessingTimeTimer(time)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[String, Integer, Integer]#OnTimerContext,
      out: Collector[Integer]
  ): Unit = {
    Option(pendingValue.value()).foreach(out.collect)
    pendingValue.clear()
    fireTime.clear()
  }

  override def flushStateDescriptor: StateDescriptor[_, _] = pendingValueDescriptor

  override def flushForCurrentKey(key: String, output: Output[StreamRecord[Integer]]): Unit = {
    Option(pendingValue.value()).foreach(value => output.collect(new StreamRecord[Integer](value)))
    BufferingTestFunction.flushedKeys.merge(key, 1, (a, b) => a + b)
    pendingValue.clear()
    fireTime.clear()
  }

  override def restoreStateDescriptor: StateDescriptor[_, _] = pendingValueDescriptor

  override def restoreForCurrentKey(key: String, timerService: InternalTimerService[VoidNamespace]): Unit = {
    BufferingTestFunction.restoredKeys.merge(key, 1, (a, b) => a + b)
    Option(fireTime.value()).foreach { originalFireTime =>
      timerService.deleteProcessingTimeTimer(VoidNamespace.INSTANCE, originalFireTime)
      val shifted = originalFireTime + shiftMillis
      timerService.registerProcessingTimeTimer(VoidNamespace.INSTANCE, shifted)
      fireTime.update(shifted)
    }
  }

}
