package pl.touk.nussknacker.engine.api.lazyy

case class LazyContext(id: String, evaluatedValues: Map[(String, Map[String, Any]), Either[Any, Throwable]] = Map.empty) {

  //TODO: for some strange reason Flink >=1.10 fails on integration tests if those types are defined in companion object...
  type Params = Map[String, Any]
  type Key = (String, Params) // (serviceId, params)

  def apply[T](serviceId: String, params: Params): T =
    getOrElse(serviceId, params, throw new RuntimeException(s"Value for service: $serviceId is not evaluated yet"))

  def getOrElse[T](serviceId: String, params: Params, default: => T): T =
    get(serviceId, params).getOrElse(default)

  def get[T](serviceId: String, params: Params): Option[T] =
    evaluatedValues.get((serviceId, params)).map {
      case Left(value) => value.asInstanceOf[T]
      case Right(ex) => throw ex
    }

  def withEvaluatedValue(serviceId: String, params: Params, value: Either[Any, Throwable]): LazyContext =
    withEvaluatedValues(Map((serviceId, params) -> value))

  def withEvaluatedValues(otherEvaluatedValues: Map[Key, Either[Any, Throwable]]): LazyContext =
    copy(evaluatedValues = evaluatedValues ++ otherEvaluatedValues)

}