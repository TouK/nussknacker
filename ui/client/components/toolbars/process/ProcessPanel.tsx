import React, {memo} from "react"
import {RootState} from "../../../reducers/index"
import {connect} from "react-redux"
import {isEmpty} from "lodash"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import ArchiveButton from "./buttons/ArchiveButton"
import UnArchiveButton from "./buttons/UnArchiveButton"
import PDFButton from "./buttons/PDFButton"
import JSONButton from "./buttons/JSONButton"
import ImportButton from "./buttons/ImportButton"
import CompareButton from "./buttons/CompareButton"
import MigrateButton from "./buttons/MigrateButton"
import {getFeatureSettings} from "../../../reducers/selectors/settings"
import {useTranslation} from "react-i18next"
import {getCapabilities} from "../../../reducers/selectors/other"
import Properties from "../status/buttons/PropertiesButton"
import {isSubprocess, isArchived} from "../../../reducers/selectors/graph"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"

type Props = StateProps

function ProcessPanel(props: Props) {
  const {capabilities, featuresSettings, isSubprocess, isArchived} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="PROCESS-PANEL" title={t("panels.process.title", "Process")}>
      <ToolbarButtons>
        {!isSubprocess ? <Properties/> : null}
        <CompareButton/>
        {capabilities.deploy && !isEmpty(featuresSettings?.remoteEnvironment) ? <MigrateButton/> : null}
        {capabilities.write ? <ImportButton/> : null}
        <JSONButton/>
        <PDFButton/>
        {capabilities.change && !isArchived ? <ArchiveButton/> : null}
        {capabilities.change && isArchived ? <UnArchiveButton/> : null}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
  capabilities: getCapabilities(state),
  isSubprocess: isSubprocess(state),
  isArchived: isArchived(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(ProcessPanel))
