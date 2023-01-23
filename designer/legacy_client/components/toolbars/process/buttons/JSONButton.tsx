import React from "react"
import {RootState} from "../../../../reducers/index"
import ProcessUtils from "../../../../common/ProcessUtils"
import {connect} from "react-redux"
import {exportProcessToJSON} from "../../../../actions/nk/importExport"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getProcessVersionId, getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/JSON.svg"
import {ToolbarButtonProps} from "../../types"

type Props = StateProps & ToolbarButtonProps

function JSONButton(props: Props) {
  const {
    processToDisplay,
    versionId,
    canExport,
    exportProcessToJSON,
    disabled,
  } = props
  const available = !disabled && canExport
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-JSON.button", "JSON")}
      icon={<Icon/>}
      disabled={!available}
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
