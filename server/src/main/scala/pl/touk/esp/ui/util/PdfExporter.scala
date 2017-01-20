package pl.touk.esp.ui.util

import java.io._
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.stream.StreamSource

import org.apache.fop.apps.FopFactory
import org.apache.xmlgraphics.util.MimeConstants
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.displayedgraph.displayablenode.NodeAdditionalFields
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails

import scala.xml.{Elem, XML}

object PdfExporter {

  val fopFactory = FopFactory.newInstance(new File(getClass.getResource("/fop/config.xml").getFile))

  def exportToPdf(svg: String, processDetails: ProcessDetails, displayableProcess: DisplayableProcess): Array[Byte] = {

    val decodedSvg = svg
      //nooo to jest dopiero hak... to po to, aby ukryc krzyzyki na linkach
      .replace("class=\"link-tools\"", "class=\"link-tools\" style=\"display: none\"")

    val fopXml = prepareFopXml(decodedSvg, processDetails, displayableProcess)

    createPdf(fopXml)
  }


  private def createPdf(fopXml: Elem): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val fop = fopFactory.newFop(MimeConstants.MIME_PDF, out)
    val src = new StreamSource(new ByteArrayInputStream(fopXml.toString().getBytes))
    TransformerFactory.newInstance().newTransformer().transform(src, new SAXResult(fop.getDefaultHandler))
    out.toByteArray
  }


  //TODO: dokladniejsze opisy wezlow, style, historia zmian/komentarze??
  private def prepareFopXml(svg: String, processDetails: ProcessDetails, displayableProcess: DisplayableProcess) = {
    val diagram = XML.loadString(svg)

    <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format" font-family="DroidSans" font-size="12pt">

      <fo:layout-master-set>
        <fo:simple-page-master margin-right="1.5cm" margin-left="1.5cm" margin-bottom="2cm" margin-top="1cm" page-width="21cm" page-height="29.7cm" master-name="left">
          <fo:region-body margin-top="0.5cm" margin-bottom="1.7cm"/>
          <fo:region-before extent="0.5cm"/>
          <fo:region-after extent="1.5cm"/>
        </fo:simple-page-master>

      </fo:layout-master-set>

      <fo:page-sequence id="N2528" master-reference="left">

        <fo:static-content flow-name="xsl-region-after">
          <fo:block text-align-last="center" font-size="10pt">
            <fo:page-number/>
          </fo:block>
        </fo:static-content>

        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="18pt" font-weight="bold" text-align="center">{processDetails.id}</fo:block>
          <fo:block>
            <fo:block font-size="16pt" font-weight="bold" space-before.minimum="1em"  space-after.minimum="2em">Version: {processDetails.processVersionId}</fo:block>
            <fo:block text-align="center">
              <fo:instream-foreign-object xmlns:svg="http://www.w3.org/2000/svg" content-width="500pt" content-height="550pt" display-align="center" text-align="center">
                {diagram}
              </fo:instream-foreign-object>
            </fo:block>
          </fo:block>

          <fo:block page-break-before="always">
            <fo:table width="100%" table-layout="fixed">
              <fo:table-column xmlns:fox="http://xmlgraphics.apache.org/fop/extensions" fox:header="true" column-width="proportional-column-width(2)"/>
              <fo:table-column column-width="proportional-column-width(1)"/>
              <fo:table-column column-width="proportional-column-width(3)"/>
              <fo:table-header font-weight="bold">
                  <fo:table-row>
                    <fo:table-cell border="1pt solid black" padding-left="1pt">
                      <fo:block>Node name</fo:block>
                    </fo:table-cell>
                    <fo:table-cell border="1pt solid black" padding-left="1pt">
                      <fo:block>Type</fo:block>
                    </fo:table-cell>
                    <fo:table-cell border="1pt solid black" padding-left="1pt">
                      <fo:block>Description</fo:block>
                    </fo:table-cell>
                  </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                  {displayableProcess.nodes.map { node =>
                    <fo:table-row>
                      <fo:table-cell border="1pt solid black" padding-left="1pt" font-weight="bold">
                        <fo:block>{node.id}</fo:block>
                      </fo:table-cell>
                      <fo:table-cell border="1pt solid black" padding-left="1pt">
                        <fo:block>{node.getClass.getSimpleName}</fo:block>
                      </fo:table-cell>
                      <fo:table-cell border="1pt solid black" padding-left="1pt">
                        <fo:block>{node.additionalFields.map(_.asInstanceOf[NodeAdditionalFields]).flatMap(_.description).getOrElse("")}</fo:block>
                      </fo:table-cell>
                    </fo:table-row>
                    }
                  }
                </fo:table-body>
            </fo:table>
          </fo:block>
        </fo:flow>
      </fo:page-sequence>

    </fo:root>
  }

}
