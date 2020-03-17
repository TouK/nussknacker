import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {showMetrics} from "../../../../actions/nk/showMetrics"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/migrate.svg"

type Props = StateProps

function MetricsButton(props: Props) {
  const {showMetrics, processId} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-metrics.button", "metrics")}
      onClick={() => showMetrics(processId)}
      icon={<Icon/>}
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
