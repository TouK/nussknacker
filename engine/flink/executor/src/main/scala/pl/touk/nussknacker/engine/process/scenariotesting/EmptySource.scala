package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.api.connector.source.{
  Boundedness,
  ReaderOutput,
  Source,
  SourceReader,
  SourceReaderContext,
  SourceSplit,
  SplitEnumerator,
  SplitEnumeratorContext
}
import org.apache.flink.core.io.{InputStatus, SimpleVersionedSerializer}

import java.util
import java.util.Collections
import java.util.concurrent.CompletableFuture
import javax.annotation.Nullable

class EmptySource[OUT] extends Source[OUT, EmptySplit.type, EmptyEnumState.type] {

  override def getBoundedness: Boundedness = Boundedness.BOUNDED

  override def createReader(readerContext: SourceReaderContext): SourceReader[OUT, EmptySplit.type] =
    new EmptySourceReader[OUT]

  override def createEnumerator(
      enumContext: SplitEnumeratorContext[EmptySplit.type]
  ): SplitEnumerator[EmptySplit.type, EmptyEnumState.type] = EmptyEnumerator

  override def restoreEnumerator(
      enumContext: SplitEnumeratorContext[EmptySplit.type],
      checkpoint: EmptyEnumState.type
  ): SplitEnumerator[EmptySplit.type, EmptyEnumState.type] = EmptyEnumerator

  override def getSplitSerializer: SimpleVersionedSerializer[EmptySplit.type] = EmptySplitSerializer

  override def getEnumeratorCheckpointSerializer: SimpleVersionedSerializer[EmptyEnumState.type] =
    EmptyEnumStateSerializer
}

object EmptySplit extends SourceSplit {
  override def splitId: String = "dummySplitId"
}

class EmptySourceReader[T] extends SourceReader[T, EmptySplit.type] {

  override def start(): Unit = {}

  override def pollNext(output: ReaderOutput[T]): InputStatus = InputStatus.END_OF_INPUT

  override def snapshotState(checkpointId: Long): util.List[EmptySplit.type] = Collections.emptyList()

  override def isAvailable: CompletableFuture[Void] = CompletableFuture.completedFuture(null)

  override def addSplits(splits: util.List[EmptySplit.type]): Unit = {}

  override def notifyNoMoreSplits(): Unit = {}

  override def close(): Unit = {}

  override def notifyCheckpointComplete(checkpointId: Long): Unit = {}

}

object EmptySplitSerializer extends SimpleVersionedSerializer[EmptySplit.type] {
  override def getVersion = 0

  override def serialize(split: EmptySplit.type): Array[Byte] = Array.empty[Byte]

  override def deserialize(version: Int, serialized: Array[Byte]): EmptySplit.type = EmptySplit
}

object EmptyEnumState

object EmptyEnumStateSerializer extends SimpleVersionedSerializer[EmptyEnumState.type] {
  override def getVersion: Int = 0

  override def serialize(obj: EmptyEnumState.type): Array[Byte] = Array.empty[Byte]

  override def deserialize(version: Int, serialized: Array[Byte]): EmptyEnumState.type = EmptyEnumState
}

object EmptyEnumerator extends SplitEnumerator[EmptySplit.type, EmptyEnumState.type] {
  override def start(): Unit = {}

  override def handleSplitRequest(subtaskId: Int, @Nullable requesterHostname: String): Unit = {}

  override def addSplitsBack(splits: util.List[EmptySplit.type], subtaskId: Int): Unit = {}

  override def addReader(subtaskId: Int): Unit = {}

  override def snapshotState(checkpointId: Long): EmptyEnumState.type = EmptyEnumState

  override def close(): Unit = {}
}
