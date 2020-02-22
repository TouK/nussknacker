/* eslint-disable i18next/no-literal-string */
import React from "react"
import {connect} from "react-redux"
import {RootState} from "../../../../reducers/index"
import {fetchProcessToDisplay} from "../../../../actions/nk/process"
import {businessViewChanged} from "../../../../actions/nk/ui/layout"
import {RightPanel} from "../../RightPanel"
import Switch from "react-switch"
import {bindActionCreators} from "redux"
import {isPristine, isBusinessView, getProcessVersionId, getProcessId} from "../../selectors/graph"

type OwnProps = {}

type Props = OwnProps & StateProps

function ViewPanel(props: Props) {
  const {nothingToSave, businessView, processId, versionId} = props

  return (
    <RightPanel title={"view"}>
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
            onHandleColor="#8fad60"
            checked={businessView}
            onChange={(checked) => {
              props.businessViewChanged(checked)
              props.fetchProcessToDisplay(processId, versionId, checked)
            }}
          />
          <span className="business-switch-text">Business View</span>
        </label>
      </div>
    </RightPanel>
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

export default connect(mapState, mapDispatch)(ViewPanel)
