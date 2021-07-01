import React from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import {bindActionCreators} from "redux"
import {fetchProcessToDisplay} from "../../../../actions/nk/process"
import {RootState} from "../../../../reducers/index"
import {getProcessId, getProcessVersionId, isBusinessView, isPristine} from "../../../../reducers/selectors/graph"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/business.svg"

type Props = StateProps

function BusinessViewSwitch(props: Props) {
  const {businessView, fetchProcessToDisplay, nothingToSave, processId, versionId} = props
  const {t} = useTranslation()
  return (
    <ToolbarButton
      name={t("panels.actions.view-businessView.label", "business")}
      icon={<Icon/>}
      isActive={businessView}
      disabled={!nothingToSave}
      onClick={() => {
        fetchProcessToDisplay(processId, versionId, !businessView)
      }}
    />
  )
}

const mapState = (state: RootState) => ({
  nothingToSave: isPristine(state) || isBusinessView(state),
  processId: getProcessId(state),
  versionId: getProcessVersionId(state),
  businessView: isBusinessView(state),
})

const mapDispatch = (dispatch) => bindActionCreators({
  fetchProcessToDisplay,
}, dispatch)

type StateProps = ReturnType<typeof mapState> & ReturnType<typeof mapDispatch>

export default connect(mapState, mapDispatch)(BusinessViewSwitch)
