import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {RootState} from "../../../../../reducers/index"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {exportProcessToPdf} from "../../../../../actions/nk/importExport"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {isBusinessView, getProcessVersionId, getProcessId} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

type OwnPropsPick = Pick<PanelOwnProps,
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
    <ButtonWithIcon
      name={t("panels.process.actions.PDF.button", "PDF")}
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
