package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source

import io.circe.Json
import PostGenericSourceFactory.{ResponseAggregationProperty, RootNameProperty, WithResponseIdProperty}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source.response.{ResponseAggregation, ResponseId}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.util.JsonHelper
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder

object PostSourceCommon {

  case class PostGenericAdditionalFields(responseAggregation: String, withResponseId: Boolean, rootName: String)

  def fetchPostGenericAdditionalFields(metaData: MetaData): PostGenericAdditionalFields =
    metaData.additionalFields.flatMap {
      case ProcessAdditionalFields(_, properties) => for {
        responseAggr <- properties.get(ResponseAggregationProperty)
        withResponseId <- properties.get(WithResponseIdProperty).map(_.toBoolean)
      } yield {
        val rootName = properties.getOrElse(RootNameProperty, "")
        PostGenericAdditionalFields(responseAggr, withResponseId, rootName)
      }
    }.getOrElse(throw new IllegalArgumentException("one of (responseAggr, withResponseId) is missing from process properties"))


  def responseEncoder[T](metaData: MetaData) = Option(new ResponseEncoder[T] {
    override def toJsonResponse(input: T, result: List[Any]): Json = {
      val postGenericAdditionalFields = fetchPostGenericAdditionalFields(metaData)
      val json = ResponseAggregation(postGenericAdditionalFields.responseAggregation).transform(metaData.id)(result)
      val jsonAsObject = JsonHelper.asObject(json, postGenericAdditionalFields.rootName)
      val jsonWithResponse = if (postGenericAdditionalFields.withResponseId) {
        ResponseId.addToJson(jsonAsObject)
      } else jsonAsObject
      Json.fromJsonObject(jsonWithResponse)
    }
  })

}
