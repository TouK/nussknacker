import React from "react"
import {DefaultToolbarPanel} from "../DefaultToolbarPanel"
import {ArchiveToggleButton} from "./buttons/ArchiveToggleButton"
import PDFButton from "./buttons/PDFButton"
import JSONButton from "./buttons/JSONButton"
import ImportButton from "./buttons/ImportButton"
import CompareButton from "./buttons/CompareButton"
import MigrateButton from "./buttons/MigrateButton"
import Properties from "../status/buttons/PropertiesButton"

function ProcessPanel(): JSX.Element {
  return (
    <DefaultToolbarPanel id="PROCESS-PANEL">
      <Properties/>
      <CompareButton/>
      <MigrateButton/>
      <ImportButton/>
      <JSONButton/>
      <PDFButton/>
      <ArchiveToggleButton/>
    </DefaultToolbarPanel>
  )
}

export default ProcessPanel
