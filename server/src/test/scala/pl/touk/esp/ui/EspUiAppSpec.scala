package pl.touk.esp.ui

import org.scalatest.FlatSpec
import pl.touk.esp.ui.util.AvailablePortFinder


class EspUiAppSpec extends FlatSpec {

  it should "start app without errors" in {
    val port = AvailablePortFinder.findAvailablePort()
    val args = Array(port.toString, "develConf/jsons")
    EspUiApp.main(args)
  }
}