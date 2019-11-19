package pl.touk.nussknacker.engine.api.dict

import io.circe.generic.JsonCodec

import scala.concurrent.Future

trait DictQueryService {

  def queryEntriesByLabel(dictId: String, labelPattern: String): Future[List[DictEntry]]

}

@JsonCodec case class DictEntry(key: String, label: String)
