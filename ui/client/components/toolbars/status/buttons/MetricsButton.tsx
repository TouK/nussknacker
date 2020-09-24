import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {showMetrics} from "../../../../actions/nk/showMetrics"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/metrics.svg"
import {pathForProcess} from "../../../../containers/Metrics"
import {PlainStyleLink} from "../../../../containers/plainStyleLink"
import {getProcessId, isSubprocess} from "../../../../reducers/selectors/graph"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"

export default function MetricsButton() {
  const dispatch = useDispatch()
  const processId = useSelector(getProcessId)
  const subprocess = useSelector(isSubprocess)

  const {t} = useTranslation()

  return (
    <PlainStyleLink to={pathForProcess(processId)}>
      <ToolbarButton
        name={t("panels.actions.deploy-metrics.button", "metrics")}
        onClick={() => dispatch(showMetrics(processId))}
        disabled={subprocess}
        icon={<Icon/>}
      />
    </PlainStyleLink>
  )
}
