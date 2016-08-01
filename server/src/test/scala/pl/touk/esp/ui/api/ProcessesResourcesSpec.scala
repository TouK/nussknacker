package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.FlatSpec
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.ui.core.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.core.process.marshall.ProcessConverter
import pl.touk.esp.ui.sample.SampleProcess

import scala.concurrent.Future

class ProcessesResourcesSpec extends FlatSpec with ScalatestRouteTest {

  it should "return 404 when no process" in {
    val route = new ProcessesResources(_ => Future.successful(None)).route

    Get("/process/123") ~> route ~> check {
      status === StatusCodes.NotFound
    }
  }

  it should "return correct json when there is some process" in {
    def sampleProcess(id: String): Future[Option[DisplayableProcess]] =
      Future.successful(
        Some(
          ProcessConverter.toDisplayable(
            ProcessCanonizer.canonize(
              SampleProcess.prepareProcess()
            )
          )
        )
      )

    val route = new ProcessesResources(sampleProcess).route

    Get("/process/123") ~> route ~> check {
      status === StatusCodes.OK
    }
  }

}
