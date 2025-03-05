package pl.touk.nussknacker.engine.api.dict

import cats.data.Validated
import io.circe.generic.JsonCodec

import scala.concurrent.{ExecutionContext, Future}

trait DictQueryService extends AutoCloseable {

  def queryEntriesByLabel(dictId: String, labelPattern: String)(
      implicit ec: ExecutionContext
  ): Validated[DictRegistry.DictNotDeclared, Future[List[DictEntry]]]

  def queryEntryByKey(dictId: String, key: String)(
      implicit ec: ExecutionContext
  ): Validated[DictRegistry.DictNotDeclared, Future[Option[DictEntry]]]

}

@JsonCodec case class DictEntry(key: String, label: String)
