package pl.touk.nussknacker.engine.api.dict

import io.circe.generic.JsonCodec

trait DictQueryService {

  def queryEntriesByLabel(dictId: String, labelPattern: String): List[DictEntry]

}

@JsonCodec case class DictEntry(key: String, label: String)
