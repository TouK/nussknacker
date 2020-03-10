import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {showMetrics} from "../../../../../actions/nk/showMetrics"
import ToolbarButton from "../../../toolbarsComponents/ToolbarButton"
import {getProcessId} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function MetricsButton(props: Props) {
  const {showMetrics, processId} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-metrics.button", "metrics")}
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
