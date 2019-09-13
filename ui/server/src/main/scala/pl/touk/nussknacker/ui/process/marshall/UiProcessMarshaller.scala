package pl.touk.nussknacker.ui.process.marshall

import argonaut.CodecJson
import argonaut.ArgonautShapeless._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.NodeAdditionalFields

object UiProcessMarshaller extends ProcessMarshaller()(
  CodecJson.derived[Option[NodeAdditionalFields]].asInstanceOf[CodecJson[Option[node.UserDefinedAdditionalNodeFields]]],
  ProcessMarshaller.additionalProcessFieldsCodec)
