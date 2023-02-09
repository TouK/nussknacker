import React, {useContext} from "react"
import {useTranslation} from "react-i18next"
import TableRowIcon from "../components/table/TableRowIcon"
import {PlainStyleLink} from "./plainStyleLink"
import {ScenariosContext} from "./ProcessTabs"

export function MetricsItem({process}: { process: $TodoType }) {
  const {t} = useTranslation()
  const {metricsLinkGetter} = useContext(ScenariosContext)
  return (
    <PlainStyleLink to={metricsLinkGetter(process.name)}>
      <TableRowIcon glyph="stats" title={t("tableRowIcon-show-metrics", "Show metrics")}/>
    </PlainStyleLink>
  )
}
