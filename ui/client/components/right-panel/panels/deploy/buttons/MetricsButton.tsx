/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {showMetrics} from "../../../../../actions/nk/showMetrics"
import {getProcessId} from "../../../selectors"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function MetricsButton(props: Props) {
  const {showMetrics, processId} = props

  return (
    <ButtonWithIcon
      name={"metrics"}
      onClick={() => showMetrics(processId)}
      icon={InlinedSvgs.buttonMetrics}
    />
  )
}

const mapState = (state: RootState) => ({
  processId: getProcessId(state),
})

const mapDispatch = {
  showMetrics,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(MetricsButton)
