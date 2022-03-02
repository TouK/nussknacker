package pl.touk.nussknacker.engine.util

class SafeLazyValues[Key, T] {

  @transient lazy val values : collection.concurrent.TrieMap[Key, T] = collection.concurrent.TrieMap()

  def getOrCreate(key: Key, create: () => T): T = {
    //TrieMap.getOrElseUpdate alone is not enough, as e.g. in Flink "espTimer" can be invoked only once - otherwise
    //Metric may be already registered, which results in refusal to register metric without feedback. In such case
    //we can end up using not-registered metric.
    //The first check is for optimization purposes - to synchronize only at the beginnning
    values.get(key) match {
      case Some(value) => value
      case None => synchronized {
        values.getOrElseUpdate(key, create())
      }
    }
  }

}
