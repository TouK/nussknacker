import React from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import Switch from "react-switch"
import {bindActionCreators} from "redux"
import {fetchProcessToDisplay} from "../../../../actions/nk/process"
import {businessViewChanged} from "../../../../actions/nk/ui/layout"
import {RootState} from "../../../../reducers/index"
import {getProcessId, getProcessVersionId, isBusinessView, isPristine} from "../../selectors/graph"

type OwnProps = {}

type Props = OwnProps & StateProps

function BussinesViewSwitch(props: Props) {
  const {nothingToSave, businessView, processId, versionId} = props
  const {t} = useTranslation()

  return (
    <div className="panel-properties">
      <label>
        <Switch
          disabled={!nothingToSave}
          uncheckedIcon={false}
          checkedIcon={false}
          height={14}
          width={28}
          offColor="#333"
          onColor="#333"
          offHandleColor="#999"
          onHandleColor="#8FAD60"
          checked={businessView}
          onChange={(checked) => {
            props.businessViewChanged(checked)
            props.fetchProcessToDisplay(processId, versionId, checked)
          }}
        />
        <span className="business-switch-text">{t("panels.actions.view-bussinesView.label", "Business View")}</span>
      </label>
    </div>
  )
}

const mapState = (state: RootState) => ({
  nothingToSave: isPristine(state),
  processId: getProcessId(state),
  versionId: getProcessVersionId(state),
  businessView: isBusinessView(state),
})

const mapDispatch = (dispatch) => bindActionCreators({
  businessViewChanged,
  fetchProcessToDisplay,
}, dispatch)

type StateProps = ReturnType<typeof mapState> & ReturnType<typeof mapDispatch>

export default connect(mapState, mapDispatch)(BussinesViewSwitch)
