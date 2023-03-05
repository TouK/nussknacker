import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {importFiles} from "../../../../actions/nk/importExport"
import {bindActionCreators} from "redux"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/import.svg"
import {ToolbarButtonProps} from "../../types"

type Props = StateProps & ToolbarButtonProps

function ImportButton(props: Props) {
  const {processId, importFiles, disabled} = props
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.process-import.button", "import")}
      icon={<Icon/>}
      disabled={disabled}
      onDrop={(files) => importFiles(files, processId)}
    />
  )
}

const mapState = (state: RootState) => ({
  processId: getProcessId(state),
})

const mapDispatch = (dispatch) => bindActionCreators({
  importFiles,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ImportButton)
