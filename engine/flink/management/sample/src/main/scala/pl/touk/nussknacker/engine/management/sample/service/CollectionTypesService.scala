package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

class CollectionTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("listParam") listParam: java.util.List[Int],
             @ParamName("mapParam") mapParam: java.util.Map[String, Int]): Future[Unit] = {
    ???
  }
}
