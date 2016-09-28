package pl.touk.esp.engine.process.api

import org.apache.flink.api.common.state.State
import org.apache.flink.core.memory.DataInputView
import org.apache.flink.runtime.state.StateHandle
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, OneInputStreamOperator}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.runtime.tasks.StreamTaskState
import pl.touk.esp.engine.util.MultiMap

/*
  Constraints
  - only EventTime
  - keys are strings
 */
trait EvictableState[In, Out] extends AbstractStreamOperator[Out] with OneInputStreamOperator[In, Out] {

  private var sortedTimers = MultiMap[Long, String](Ordering.Long)

  //musimy umiec szybko dobierac sie do sortedTimers, wiec tutaj przechowujemy oddzielna mape...
  private var timersByKey = Map[String, Long]()

  def getState: State

  override def processWatermark(mark: Watermark) = {
    val time = mark.getTimestamp

    val toRemove = sortedTimers.until(time)

    toRemove.map.values.flatten.foreach { key =>
      getStateBackend.setCurrentKey(key)
      getState.clear()
      timersByKey -= key
    }
    sortedTimers = sortedTimers.from(time)
  }

  protected final def setEvictionTimeForCurrentKey(time: Long) = {
    val key = getStateBackend.getCurrentKey.toString
    setEvictionTime(key, time)
  }

  private def setEvictionTime(key: String, time: Long) = {

    timersByKey.get(key).foreach { previousTime =>
      sortedTimers = sortedTimers.remove(previousTime, key)
    }
    sortedTimers = sortedTimers.add(time, key)
    timersByKey += (key -> time)
  }


  override def restoreState(state: StreamTaskState) = {
    super.restoreState(state)
    val userClassloader = getUserCodeClassloader

    val inputState: StateHandle[DataInputView] = state.getOperatorState.asInstanceOf[StateHandle[DataInputView]]
    val in: DataInputView = inputState.getState(userClassloader)

    val timersCount = in.readInt()
    (1 to timersCount).foreach { _ =>
      val key = in.readUTF()
      val ts = in.readLong()
      setEvictionTime(key, ts)
    }
  }

  override def snapshotOperatorState(checkpointId: Long, timestamp: Long) = {
    val savedState = super.snapshotOperatorState(checkpointId, timestamp)
    val out = getStateBackend.createCheckpointStateOutputView(checkpointId, timestamp)

    out.writeInt(timersByKey.size)
    timersByKey.foreach { case (key, time) =>
      out.writeUTF(key)
      out.writeLong(time)
    }
    savedState.setOperatorState(out.closeAndGetHandle)
    savedState
  }

}

