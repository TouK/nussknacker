import React, {memo} from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {isEmpty} from "lodash"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import ArchiveButton from "./buttons/ArchiveButton"
import PDFButton from "./buttons/PDFButton"
import JSONButton from "./buttons/JSONButton"
import ImportButton from "./buttons/ImportButton"
import CompareButton from "./buttons/CompareButton"
import MigrateButton from "./buttons/MigrateButton"
import {getFeatureSettings} from "../../selectors/settings"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../UserRightPanel"
import {getCapabilities} from "../../selectors/other"
import Properties from "../edit/buttons/PropertiesButton"
import {isSubprocess} from "../../selectors/graph"

type OwnPropsPick = Pick<PassedProps,
  | "exportGraph">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function ProcessPanel(props: Props) {
  const {capabilities, exportGraph, featuresSettings, isSubprocess} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="PROCESS-PANEL" title={t("panels.process.title", "Process")}>
      {!isSubprocess ? <Properties/> : null}
      <CompareButton/>
      {capabilities.deploy && !isEmpty(featuresSettings?.remoteEnvironment) ? <MigrateButton/> : null}
      {capabilities.write ? <ImportButton/> : null}
      <JSONButton/>
      <PDFButton exportGraph={exportGraph}/>
      {capabilities.write ? <ArchiveButton/> : null}
    </CollapsibleToolbar>
  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
  capabilities: getCapabilities(state),
  isSubprocess: isSubprocess(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(ProcessPanel))
