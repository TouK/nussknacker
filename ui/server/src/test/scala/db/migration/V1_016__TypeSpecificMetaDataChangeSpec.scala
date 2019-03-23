package db.migration

import argonaut.Parse
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.ProcessAdditionalFields
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller

class V1_016__TypeSpecificMetaDataChangeSpec extends FlatSpec with Matchers {

  it should "convert json" in {

    implicit val marshaller = UiProcessMarshaller

    val oldJson =
      Parse.parse("""{"metaData":{"id":"DEFGH","parallelism":3, "additionalFields":{"groups":[]}}, "exceptionHandlerRef": {"parameters":[]},"nodes":[]}""").right.get

    val converted = V1_016__TypeSpecificMetaDataChange.updateMetaData(oldJson).flatMap(js => marshaller.fromJson(js.nospaces).toOption)

    val metaData = converted.map(_.metaData)
    

    metaData shouldBe Some(MetaData("DEFGH", StreamMetaData(parallelism = Some(3)), false, Some(ProcessAdditionalFields(None, Set()))))
  }
}