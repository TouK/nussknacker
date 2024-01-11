package pl.touk.nussknacker.engine.process.helpers

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

/**
  * This class purpose is for communication between Flink's functions and a unit test code.
  * Instances of this classes should be kept in the scala object (in some static context) and passed to Flink functions
  * by name to avoid serialization of TestResultsHolder itself (only scala object should be marked as Serializable).
  *
  * You should also create dedicated scala objects for each test to make sure that Flink function will "communicate"
  * with the same holder as passed, not some other from other test.
  * // TODO: We could create one scala object and kept data keyed by TestRunId as we do with ResultsCollectingListenerHolder
  */
class TestResultsHolder[T] {

  private val resultsList = new CopyOnWriteArrayList[T]

  def add(element: T): Unit = resultsList.add(element)

  def results: List[T] = {
    resultsList.toArray.toList.map(_.asInstanceOf[T])
  }

  def clear(predicate: Predicate[T] = _ => true): Unit = {
    resultsList.removeIf(predicate)
  }

}
