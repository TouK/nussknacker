import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {importFiles} from "../../../../actions/nk/importExport"
import {reportEvent} from "../../../../actions/nk/reportEvent"
import {bindActionCreators} from "redux"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/import.svg"
import {ToolbarButtonProps} from "../../types"

type Props = StateProps & ToolbarButtonProps

function ImportButton(props: Props) {
  const {processId, importFiles, reportEvent, disabled} = props
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.process-import.button", "import")}
      icon={<Icon/>}
      disabled={disabled}
      onClick={() => reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "import",
      })}
      onDrop={(files) => importFiles(files, processId)}
    />
  )
}

const mapState = (state: RootState) => ({
  processId: getProcessId(state),
})

const mapDispatch = (dispatch) => bindActionCreators({
  importFiles,
  reportEvent,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ImportButton)
