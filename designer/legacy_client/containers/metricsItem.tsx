import React from "react"
import {useTranslation} from "react-i18next"
import TableRowIcon from "../components/table/TableRowIcon"
import {PlainStyleLink} from "./plainStyleLink"

export function MetricsItem({process}: { process: $TodoType }) {
  const {t} = useTranslation()
  return (
    <PlainStyleLink to={`/metrics/${process.name}`}>
      <TableRowIcon glyph="stats" title={t("tableRowIcon-show-metrics", "Show metrics")}/>
    </PlainStyleLink>
  )
}
