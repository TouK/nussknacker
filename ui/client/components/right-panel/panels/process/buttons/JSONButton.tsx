import React from "react"
import {RootState} from "../../../../../reducers/index"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {exportProcessToJSON} from "../../../../../actions/nk/importExport"
import {ToolbarButton} from "../../../ToolbarButton"
import {getProcessVersionId, getProcessToDisplay} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function JSONButton(props: Props) {
  const {
    processToDisplay, versionId, canExport,
    exportProcessToJSON,
  } = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-JSON.button", "JSON")}
      icon={InlinedSvgs.buttonExport}
      disabled={!canExport}
      onClick={() => exportProcessToJSON(processToDisplay, versionId)}
    />
  )
}

const mapState = (state: RootState) => {
  return {
    versionId: getProcessVersionId(state),
    processToDisplay: getProcessToDisplay(state),
    canExport: ProcessUtils.canExport(state),
  }
}

const mapDispatch = {
  exportProcessToJSON,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(JSONButton)
