import React from "react"
import {RootState} from "../../../../reducers/index"
import ProcessUtils from "../../../../common/ProcessUtils"
import {connect} from "react-redux"
import {exportProcessToPdf} from "../../../../actions/nk/importExport"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {isBusinessView, getProcessVersionId, getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {useGraph} from "../../../graph/GraphContext"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/PDF.svg"
import {ToolbarButtonProps} from "../../types"

type Props = StateProps & ToolbarButtonProps

function PDFButton(props: Props) {
  const {processId, businessView, versionId, canExport, exportProcessToPdf, disabled} = props
  const available = !disabled && canExport
  const {t} = useTranslation()
  const graphGetter = useGraph()

  return (
    <ToolbarButton
      name={t("panels.actions.process-PDF.button", "PDF")}
      icon={<Icon/>}
      disabled={!available}
      onClick={async () => {
        // TODO: add busy indicator
        // TODO: try to do this in worker
        // TODO: try to do this more in redux/react style
        const exportedGraph = await graphGetter().exportGraph()
        exportProcessToPdf(processId, versionId, exportedGraph, businessView)
      }}
    />
  )
}

const mapState = (state: RootState) => {
  return {
    processId: getProcessId(state),
    versionId: getProcessVersionId(state),
    canExport: ProcessUtils.canExport(state),
    businessView: isBusinessView(state),
  }
}

const mapDispatch = {
  exportProcessToPdf,
}
type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(PDFButton)
