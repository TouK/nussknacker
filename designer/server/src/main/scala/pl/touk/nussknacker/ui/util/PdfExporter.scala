package pl.touk.nussknacker.ui.util

import java.io._
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.stream.StreamSource
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.IOUtils
import org.apache.fop.apps.FopConfParser
import org.apache.fop.apps.io.ResourceResolverFactory
import org.apache.xmlgraphics.util.MimeConstants
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

import scala.xml.{Elem, NodeSeq, XML}

object PdfExporter extends LazyLogging {

  private val fopFactory = new FopConfParser(
    getClass.getResourceAsStream("/fop/config.xml"),
    new URI("http://touk.pl"),
    ResourceResolverFactory.createDefaultResourceResolver
  ).getFopFactoryBuilder.build

  def exportToPdf(
      svg: String,
      processDetails: ScenarioWithDetailsEntity[ScenarioGraph],
      processActivity: ProcessActivity
  ): Array[Byte] = {

    // initFontsIfNeeded is invoked every time to make sure that /tmp content is not deleted
    initFontsIfNeeded()
    // FIXME: cannot render polish signs..., better to strip them than not render anything...
    // \u00A0 - non-breaking space in not ASCII :)...
    val fopXml = prepareFopXml(
      svg.replaceAll("\u00A0", " ").replaceAll("[^\\p{ASCII}]", ""),
      processDetails,
      processActivity,
      processDetails.json
    )

    createPdf(fopXml)
  }

  // in PDF export we print timezone, to avoid ambiguity
  // TODO: pass client timezone from FE
  private def format(instant: Instant) = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss [VV]")
    instant.atZone(ZoneId.systemDefault()).format(formatter)
  }

  // TODO: this is one nasty hack, is there a better way to make fop read fonts from classpath?
  private def initFontsIfNeeded(): Unit = synchronized {
    val dir = new File("/tmp/fop/fonts")
    dir.mkdirs()
    List(
      "OpenSans-BoldItalic.ttf",
      "OpenSans-Bold.ttf",
      "OpenSans-ExtraBoldItalic.ttf",
      "OpenSans-ExtraBold.ttf",
      "OpenSans-Italic.ttf",
      "OpenSans-LightItalic.ttf",
      "OpenSans-Light.ttf",
      "OpenSans-Regular.ttf",
      "OpenSans-SemiboldItalic.ttf",
      "OpenSans-Semibold.ttf"
    ).filterNot(name => new File(dir, name).exists()).foreach { name =>
      IOUtils.copy(getClass.getResourceAsStream(s"/fop/fonts/$name"), new FileOutputStream(new File(dir, name)))
    }
  }

  private def createPdf(fopXml: Elem): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val fop = fopFactory.newFop(MimeConstants.MIME_PDF, out)
    val src = new StreamSource(new ByteArrayInputStream(fopXml.toString().getBytes(StandardCharsets.UTF_8)))
    TransformerFactory.newInstance().newTransformer().transform(src, new SAXResult(fop.getDefaultHandler))
    out.toByteArray
  }

  private[util] def prepareFopXml(
      svg: String,
      processDetails: ScenarioWithDetailsEntity[ScenarioGraph],
      processActivity: ProcessActivity,
      scenarioGraph: ScenarioGraph
  ) = {
    val diagram        = XML.loadString(svg)
    val currentVersion = processDetails.history.get.find(_.processVersionId == processDetails.processVersionId).get

    <root xmlns="http://www.w3.org/1999/XSL/Format" font-family="OpenSans" font-size="12pt" xml:lang="en">

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
            {processDetails.name}
            (
            {processDetails.processCategory}
            )
          </block>
          <block>
            <block font-size="14pt" space-before.minimum="1em">
              Version:
              {processDetails.processVersionId}
            </block>
            <block font-size="14pt" space-before.minimum="0.5em">
              Saved by
              {currentVersion.user}
              at
              {format(currentVersion.createDate)}
            </block>
            <block text-align="left" space-before.minimum="0.5em">
              {processDetails.description.getOrElse("")}
            </block>
            <block text-align="center" space-before.minimum="3em">
              <instream-foreign-object xmlns:svg="http://www.w3.org/2000/svg" content-width="500pt" content-height="400pt" display-align="center" text-align="center">
                {diagram}
              </instream-foreign-object>
            </block>
          </block>{nodesSummary(scenarioGraph)}<block font-size="15pt" font-weight="bold" text-align="left">
          Nodes details
        </block>{scenarioGraph.nodes.map(nodeDetails)}{comments(processActivity)}{attachments(processActivity)}
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
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(3)"/>
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(3)"/>
        <table-column column-width="proportional-column-width(7)"/>
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
      <table-cell>
            <block/>
          </table-cell>
    } else
      processActivity.comments.sortBy(_.createDate).map { comment =>
        <table-row>
              <table-cell border="1pt solid black" padding-left="1pt">
                <block>
                  {format(comment.createDate)}
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
      }
  }
        </table-body>
      </table>
    </block>
  </block>

  private def nodeDetails(node: NodeData) = {
    val nodeData: List[(String, String)] = node match {
      case Source(_, SourceRef(typ, params), _) =>
        ("Type", typ) :: params.map(p => (p.name.value, p.expression.expression))
      case Filter(_, expression, _, _) => List(("Expression", expression.expression))
      case Enricher(_, ServiceRef(typ, params), output, _) =>
        ("Type", typ) :: ("Output", output) :: params.map(p => (p.name.value, p.expression.expression))
      // TODO: what about Swtich??
      case Switch(_, expression, exprVal, _) => expression.map(e => ("Expression", e.expression)).toList
      case Processor(_, ServiceRef(typ, params), _, _) =>
        ("Type", typ) :: params.map(p => (p.name.value, p.expression.expression))
      case Sink(_, SinkRef(typ, params), _, _, _) =>
        ("Type", typ) :: params.map(p => (p.name.value, p.expression.expression))
      case CustomNode(_, output, typ, params, _) =>
        ("Type", typ) :: ("Output", output.getOrElse("")) :: params.map(p => (p.name.value, p.expression.expression))
      case FragmentInput(_, FragmentRef(typ, params, _), _, _, _) =>
        ("Type", typ) :: params.map(p => (p.name.value, p.expression.expression))
      case FragmentInputDefinition(_, parameters, _) => parameters.map(p => p.name.value -> p.typ.refClazzName)
      case FragmentOutputDefinition(_, outputName, fields, _) =>
        ("Output name", outputName) :: fields.map(p => p.name -> p.expression.expression)
      case Variable(_, name, expr, _) => (name -> expr.expression) :: Nil
      case VariableBuilder(_, name, fields, _) =>
        ("Variable name", name) :: fields.map(p => p.name -> p.expression.expression)
      case Join(_, output, typ, parameters, branch, _) =>
        ("Type", typ) :: ("Output", output.getOrElse("")) ::
          parameters.map(p => p.name.value -> p.expression.expression) ++ branch.flatMap(bp =>
            bp.parameters.map(p => s"${bp.branchId} - ${p.name}" -> p.expression.expression)
          )
      case Split(_, _) => ("No parameters", "") :: Nil
      // This should not happen in properly resolved scenario...
      case _: BranchEndData       => throw new IllegalArgumentException("Should not happen during PDF export")
      case _: FragmentUsageOutput => throw new IllegalArgumentException("Should not happen during PDF export")
    }
    val data: List[(String, String)] = node.additionalFields
      .flatMap(_.description)
      .map(naf => ("Description", naf))
      .toList ++ nodeData
    if (data.isEmpty) {
      NodeSeq.Empty
    } else {
      <block margin-bottom="25pt" margin-top="5pt">
        <block font-size="13pt" font-weight="bold" text-align="left" id={node.id}>
          {node.getClass.getSimpleName}{node.id}
        </block>
        <table width="100%" table-layout="fixed">
          <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(2)"/>
          <table-column column-width="proportional-column-width(3)"/>
          <table-body>
            {
        data.map { case (key: String, value: String) =>
          <table-row>
              <table-cell border="1pt solid black" padding-left="1pt" font-weight="bold">
                <block>
                  {key}
                </block>
              </table-cell>
              <table-cell border="1pt solid black" padding-left="1pt">
                <block>
                  {addEmptySpace(value)}
                </block>
              </table-cell>
            </table-row>
        }
      }
          </table-body>
        </table>
      </block>
    }

  }

  // we want to be able to break line for these characters. it's not really perfect solution for long, complex expressions,
  // but should handle most of the cases../
  private def addEmptySpace(str: String) = List(")", ".", "(")
    .foldLeft(str) { (acc, el) => acc.replace(el, el + '\u200b') }

  private def nodesSummary(scenarioGraph: ScenarioGraph) = {
    <block page-break-before="always" space-after.minimum="3em">
      <block font-size="15pt" font-weight="bold" text-align="left">
        Nodes summary
      </block>
      <table width="100%" table-layout="fixed">
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(3)"/>
        <table-column column-width="proportional-column-width(2)"/>
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
      if (scenarioGraph.nodes.isEmpty) {
        <table-cell>
            <block/>
          </table-cell>
      } else
        scenarioGraph.nodes.map { node =>
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
                  {node.additionalFields.flatMap(_.description).getOrElse("")}
                </block>
              </table-cell>
            </table-row>
        }
    }
        </table-body>
      </table>
    </block>
  }

  private def attachments(processActivity: ProcessActivity) = if (processActivity.attachments.isEmpty) {
    <block/>
  } else {
    <block space-after.minimum="3em">
      <block font-size="15pt" font-weight="bold" text-align="left">
        Attachments
      </block>
      <table width="100%" table-layout="fixed">
        <table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true"
                      column-width="proportional-column-width(3)"/>
        <table-column column-width="proportional-column-width(3)"/>
        <table-column column-width="proportional-column-width(7)"/>
        <table-header font-weight="bold">
          <table-row>


            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Date</block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>Author</block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>File name</block>
            </table-cell>
          </table-row>
        </table-header>
        <table-body>
          {
      processActivity.attachments
        .sortBy(_.createDate)
        .map(attachment => <table-row>

            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {format(attachment.createDate)}
              </block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {attachment.user}
              </block>
            </table-cell>
            <table-cell border="1pt solid black" padding-left="1pt">
              <block>
                {attachment.fileName}
              </block>
            </table-cell>
          </table-row>)
    }
        </table-body>
      </table>
    </block>
  }

}
