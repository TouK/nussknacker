import React from "react"
import {RootState} from "../../../../../reducers/index"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {connect} from "react-redux"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {exportProcessToPdf} from "../../../../../actions/nk/importExport"
import {ToolbarButton} from "../../../ToolbarButton"
import {isBusinessView, getProcessVersionId, getProcessId} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../../ToolsLayer"

type OwnPropsPick = Pick<PassedProps,
  | "exportGraph">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function PDFButton(props: Props) {
  const {
    exportGraph, processId, businessView, versionId, canExport,
    exportProcessToPdf,
  } = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-PDF.button", "PDF")}
      icon={InlinedSvgs.pdf}
      disabled={!canExport}
      onClick={() => exportProcessToPdf(processId, versionId, exportGraph(), businessView)}
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
