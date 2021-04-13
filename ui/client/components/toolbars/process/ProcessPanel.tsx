import React from "react"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import {ArchiveToggleButton} from "./buttons/ArchiveToggleButton"
import PDFButton from "./buttons/PDFButton"
import JSONButton from "./buttons/JSONButton"
import ImportButton from "./buttons/ImportButton"
import CompareButton from "./buttons/CompareButton"
import MigrateButton from "./buttons/MigrateButton"
import {useTranslation} from "react-i18next"
import Properties from "../status/buttons/PropertiesButton"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"

function ProcessPanel(): JSX.Element {
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="PROCESS-PANEL" title={t("panels.process.title", "Process")}>
      <ToolbarButtons>
        <Properties/>
        <CompareButton/>
        <MigrateButton/>
        <ImportButton/>
        <JSONButton/>
        <PDFButton/>
        <ArchiveToggleButton/>
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default ProcessPanel
