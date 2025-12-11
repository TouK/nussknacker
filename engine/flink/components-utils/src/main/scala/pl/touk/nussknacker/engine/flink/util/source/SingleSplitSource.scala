package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.connector.source.{SourceReader, SourceSplit, SplitEnumerator, SplitEnumeratorContext}
import org.apache.flink.core.io.SimpleVersionedSerializer
import pl.touk.nussknacker.engine.flink.util.source.SingleSplitSource.{
  SingleSplit,
  SingleSplitEnumerator,
  SingleSplitSerializer
}

import java.util
import java.util.Collections
import java.util.concurrent.CompletableFuture

abstract class SingleSplitSource[T] extends NoEnumeratorCheckpointsSource[T, SingleSplit] {

  override def getSplitSerializer: SimpleVersionedSerializer[SingleSplit] = SingleSplitSerializer

  override def createEnumerator(
      enumContext: SplitEnumeratorContext[SingleSplit]
  ): SplitEnumerator[SingleSplit, Unit] = new SingleSplitEnumerator(enumContext)

  override def restoreEnumerator(
      enumContext: SplitEnumeratorContext[SingleSplit],
      checkpoint: Unit
  ): SplitEnumerator[SingleSplit, Unit] = new SingleSplitEnumerator(enumContext)

}

object SingleSplitSource {

  object SingleSplit extends SingleSplit

  class SingleSplit extends SourceSplit {

    override val splitId: String = "SINGLE"

  }

  private object SingleSplitSerializer extends SimpleVersionedSerializer[SingleSplit] {

    override def getVersion: Int = 1

    override def serialize(obj: SingleSplit): Array[Byte] = Array.empty[Byte]

    override def deserialize(version: Int, serialized: Array[Byte]): SingleSplit = SingleSplit

  }

  private class SingleSplitEnumerator(context: SplitEnumeratorContext[SingleSplit])
      extends SplitEnumerator[SingleSplit, Unit] {

    override def start(): Unit = {
      // No-op implementation
    }

    override def handleSplitRequest(subtaskId: Int, requesterHostname: String): Unit = {
      // Most common case: split request means reader is ready for work
      addReader(subtaskId)
    }

    override def addSplitsBack(splits: util.List[SingleSplit], subtaskId: Int): Unit = {
      // No-op implementation
    }

    override def addReader(subtaskId: Int): Unit = {
      // Signal no more splits to ensure immediate completion
      context.signalNoMoreSplits(subtaskId)
    }

    override def close(): Unit = {
      // No-op implementation
    }

    override def snapshotState(checkpointId: Long): Unit = ()
  }

  trait Reader[T] extends SourceReader[T, SingleSplit] {

    override def snapshotState(checkpointId: Long): util.List[SingleSplit] = Collections.singletonList(SingleSplit)

    override def addSplits(splits: util.List[SingleSplit]): Unit = {
      // No-op implementation
    }

    override def notifyNoMoreSplits(): Unit = {
      // No-op implementation
    }

    // By default, the lifecycle is simple
    override def isAvailable: CompletableFuture[Void] = CompletableFuture.completedFuture(null)

    override def start(): Unit = {}

    override def close(): Unit = {}

  }

}
