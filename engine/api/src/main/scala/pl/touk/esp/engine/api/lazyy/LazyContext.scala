package pl.touk.esp.engine.api.lazyy

import pl.touk.esp.engine.api.lazyy.LazyContext._

case class LazyContext(evaluatedValues: Map[LazyContext.Key, Either[Any, Throwable]] = Map.empty) {

  def apply[T](serviceId: String, params: Params): T =
    getOrElse(serviceId, params, throw new RuntimeException(s"Value for service: $serviceId is not evaluated yet"))

  def getOrElse[T](serviceId: String, params: Params, default: => T) =
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

object LazyContext {

  type Key = (String, Params) // (serviceId, params)
  type Params = Map[String, Any]


}