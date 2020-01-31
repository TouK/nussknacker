package pl.touk.nussknacker.engine.api.lazyy

import pl.touk.nussknacker.engine.api.lazyy.LazyContext._

case class LazyContext(id: String, evaluatedValues: Map[(String, Map[String, Any]), Either[Any, Throwable]] = Map.empty) {

  def apply[T](serviceId: String, params: Map[String, Any]): T =
    getOrElse(serviceId, params, throw new RuntimeException(s"Value for service: $serviceId is not evaluated yet"))

  def getOrElse[T](serviceId: String, params: Map[String, Any], default: => T) =
    get(serviceId, params).getOrElse(default)

  def get[T](serviceId: String, params: Map[String, Any]): Option[T] =
    evaluatedValues.get((serviceId, params)).map {
      case Left(value) => value.asInstanceOf[T]
      case Right(ex) => throw ex
    }

  def withEvaluatedValue(serviceId: String, params: Map[String, Any], value: Either[Any, Throwable]): LazyContext =
    withEvaluatedValues(Map((serviceId, params) -> value))

  def withEvaluatedValues(otherEvaluatedValues: Map[(String, Map[String, Any]), Either[Any, Throwable]]): LazyContext =
    copy(evaluatedValues = evaluatedValues ++ otherEvaluatedValues)

}

object LazyContext {

  //sth is wrong here...
  //type Key = (String, Map[String, Any]) // (serviceId, params)


}