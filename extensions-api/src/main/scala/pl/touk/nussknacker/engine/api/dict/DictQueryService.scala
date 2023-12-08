package pl.touk.nussknacker.engine.api.dict

import cats.data.Validated

import scala.concurrent.{ExecutionContext, Future}

trait DictQueryService extends AutoCloseable {

  def queryEntriesByLabel(dictId: String, labelPattern: String)(
      implicit ec: ExecutionContext
  ): Validated[DictRegistry.DictNotDeclared, Future[List[DictEntry]]]

}

case class DictEntry(key: String, label: String)
