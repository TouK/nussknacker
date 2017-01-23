package pl.touk.esp.ui.util

import java.io._
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.stream.StreamSource

import org.apache.fop.apps.FopFactory
import org.apache.xmlgraphics.util.MimeConstants
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.displayedgraph.displayablenode.NodeAdditionalFields
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails

import scala.xml.{Elem, NodeSeq, XML}

object PdfExporter {

  val fopFactory = FopFactory.newInstance(new File(getClass.getResource("/fop/config.xml").getFile))

  def exportToPdf(svg: String, processDetails: ProcessDetails, processActivity: ProcessActivity, displayableProcess: DisplayableProcess): Array[Byte] = {

    val decodedSvg = svg
      //nooo to jest dopiero hak... to po to, aby ukryc krzyzyki na linkach
      .replace("class=\"link-tools\"", "class=\"link-tools\" style=\"display: none\"")

    val fopXml = prepareFopXml(decodedSvg, processDetails, processActivity, displayableProcess)

    createPdf(fopXml)
  }


  private def createPdf(fopXml: Elem): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val fop = fopFactory.newFop(MimeConstants.MIME_PDF, out)
    val src = new StreamSource(new ByteArrayInputStream(fopXml.toString().getBytes))
    TransformerFactory.newInstance().newTransformer().transform(src, new SAXResult(fop.getDefaultHandler))
    out.toByteArray
  }

  val formatter = DateTimeFormatter.ofPattern("YYYY-MM-DD HH:mm:ss")

  //TODO: dokladniejsze opisy wezlow, style, historia zmian/komentarze??
  private def prepareFopXml(svg: String, processDetails: ProcessDetails, processActivity: ProcessActivity, displayableProcess: DisplayableProcess) = {
    val diagram = XML.loadString(svg)
    val currentVersion = processDetails.history.find(_.processVersionId == processDetails.processVersionId).get

    <root xmlns="http://www.w3.org/1999/XSL/Format" font-family="DroidSans" font-size="12pt">

      <layout-master-set>
        <simple-page-master margin-right="1.5cm" margin-left="1.5cm" margin-bottom="2cm" margin-top="1cm" page-width="21cm" page-height="29.7cm" master-name="left">
          <region-body margin-top="0.5cm" margin-bottom="2cm"/>
          <region-after extent="0.5cm"/>
        </simple-page-master>

      </layout-master-set>

      <page-sequence id="N2528" master-reference="left">

        <static-content flow-name="xsl-region-after">
          <block text-align-last="center" font-size="10pt">
            <page-number/>
          </block>
        </static-content>

        <flow flow-name="xsl-region-body">
          <block font-size="16pt" font-weight="bold" text-align="center">
            {processDetails.id} ({processDetails.processCategory})
          </block>
          <block>
            <block font-size="14pt" space-before.minimum="1em">
              Version: {processDetails.processVersionId}
            </block>
            <block font-size="14pt" space-before.minimum="0.5em">
              Saved by {currentVersion.user} at {currentVersion.createDate.format(formatter)}
            </block>
            <block text-align="left" space-before.minimum="0.5em">
              {processDetails.description.getOrElse("")}
            </block>
            <block text-align="center" space-before.minimum="3em">
              <instream-foreign-object xmlns:svg="http://www.w3.org/2000/svg" content-width="500pt" content-height="400pt" display-align="center" text-align="center">
                {diagram}
              </instream-foreign-object>
            </block>
          </block>

          {nodesSummary(displayableProcess)}
          <block font-size="15pt" font-weight="bold" text-align="left">
            Nodes details
          </block>
          {displayableProcess.nodes.map(nodeDetails)}
          {comments(processActivity)}
        </flow>
      </page-sequence>

    </root>
  }

  private def comments(processActivity: ProcessActivity) = <block>
    <block margin-bottom="25pt" margin-top="5pt">
      <block font-size="15pt" font-weight="bold" text-align="left">
        Comments
      </block>
      <table width="100%" table-layout="fixed">
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(1)"/>
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(1)"/>
        <table-column column-width="proportional-column-width(3)"/>
        <table-header font-weight="bold">
          <table-row>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Date</block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Author</block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Comment</block>
            </table-cell>
          </table-row>
        </table-header>
        <table-body>
          {
          if (processActivity.comments.isEmpty) {
            <table-cell><block /></table-cell>
          } else
          processActivity.comments.sortBy(_.createDate.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli).map { comment =>
          <table-row>
            <table-cell border="1pt solid black" padding-left="1pt">
               <block>
                 {comment.createDate.format(formatter)}
               </block>
             </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {comment.user}
              </block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {comment.content}
              </block>
            </table-cell>
          </table-row>
        }}
        </table-body>
      </table>
    </block>
  </block>

  private def attachments(processActivity: ProcessActivity) = {
    <block/>
  }


  private def nodeDetails(node: NodeData) = {
    val nodeData = node match {
      case Source(_, SourceRef(typ, params), _) => ("Type", typ) :: params.map(p => (p.name, p.value))
      case Filter(_, expression, _) => List(("Expression", expression.expression))
      case Enricher(_, ServiceRef(typ, params), output, _) => ("Type", typ) :: ("Output", output) :: params.map(p => (p.name, p.expression.expression))
        //TODO: jak zwykle - co ze switchem??
      case Switch(_, expression, exprVal, _) => List(("Expression", expression.expression))
      case Processor(_, ServiceRef(typ, params), _) => ("Type", typ) :: params.map(p => (p.name, p.expression.expression))
      case Sink(_, SinkRef(typ, params), output, _) => ("Type", typ) :: output.map(expr => ("Output", expr.expression)).toList ++ params.map(p => (p.name, p.value))
      case CustomNode(_, output, typ, params, _) => ("Type", typ) :: ("Output", output) :: params.map(p => (p.name, p.expression.expression))
      //TODO: variable, variable builder,
      case _ => List()
    }
    val data = node.additionalFields
      .map(_.asInstanceOf[NodeAdditionalFields])
      .flatMap(_.description)
      .map(naf => ("Description", naf)).toList ++ nodeData
    if (data.isEmpty) {
      NodeSeq.Empty
    } else {
      <block margin-bottom="25pt" margin-top="5pt">
        <block font-size="13pt" font-weight="bold" text-align="left" id={node.id}>
          {node.getClass.getSimpleName} {node.id}
        </block>
        <table width="100%" table-layout="fixed">
          <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(2)"/>
          <table-column column-width="proportional-column-width(3)"/>
          <table-body>
            {data.map { case (key, value) =>
            <table-row>
              <table-cell border="1pt solid black" padding-left="1pt" font-weight="bold">
                <block>
                  {key}
                </block>
              </table-cell>
              <table-cell border="1pt solid black" padding-left="1pt">
                <block>
                  {value}
                </block>
              </table-cell>
            </table-row>
          }}
          </table-body>
        </table>
      </block>
    }
  }

  private def nodesSummary(displayableProcess: DisplayableProcess) = {
    <block page-break-before="always" space-after.minimum="3em">
      <block font-size="15pt" font-weight="bold" text-align="left">
        Nodes summary
      </block>
      <table width="100%" table-layout="fixed">
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(2)"/>
        <table-column column-width="proportional-column-width(1)"/>
        <table-column column-width="proportional-column-width(3)"/>
        <table-header font-weight="bold">
          <table-row>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Node name</block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Type</block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Description</block>
            </table-cell>
          </table-row>
        </table-header>
        <table-body>
          {
          if (displayableProcess.nodes.isEmpty) {
             <table-cell><block /></table-cell>
           } else
          displayableProcess.nodes.map { node =>
          <table-row>
            <table-cell border="1pt solid black" padding-left="1pt" font-weight="bold">
              <block>
                <basic-link internal-destination={node.id}>
                  {node.id}
                </basic-link>
              </block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {node.getClass.getSimpleName}
              </block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {node.additionalFields.map(_.asInstanceOf[NodeAdditionalFields]).flatMap(_.description).getOrElse("")}
              </block>
            </table-cell>
          </table-row>
        }}
        </table-body>
      </table>
    </block>
  }

}
