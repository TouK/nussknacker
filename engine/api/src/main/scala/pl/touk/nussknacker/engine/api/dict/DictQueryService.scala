package pl.touk.nussknacker.engine.api.dict

import java.io.Closeable

import cats.data.Validated
import io.circe.generic.JsonCodec

import scala.concurrent.{ExecutionContext, Future}

trait DictQueryService extends Closeable {
  
  def queryEntriesByLabel(dictId: String, labelPattern: String)
                         (implicit ec: ExecutionContext): Validated[DictRegistry.DictNotDeclared, Future[List[DictEntry]]]

}

@JsonCodec case class DictEntry(key: String, label: String)
