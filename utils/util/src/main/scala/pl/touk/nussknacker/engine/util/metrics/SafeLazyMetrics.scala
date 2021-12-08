package pl.touk.nussknacker.engine.util.metrics

class SafeLazyMetrics[Key, Metric] {

  @transient lazy val metrics : collection.concurrent.TrieMap[Key, Metric] = collection.concurrent.TrieMap()

  def getOrCreate(key: Key, create: () => Metric): Metric = {
    //TrieMap.getOrElseUpdate alone is not enough, as e.g. in Flink "espTimer" can be invoked only once - otherwise
    //Metric may be already registered, which results in refusal to register metric without feedback. In such case
    //we can end up using not-registered metric.
    //The first check is for optimization purposes - to synchronize only at the beginnning
    metrics.get(key) match {
      case Some(value) => value
      case None => synchronized {
        metrics.getOrElseUpdate(key, create())
      }
    }
  }

}
