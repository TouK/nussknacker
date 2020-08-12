import {useTranslation} from "react-i18next"
import TableRowIcon from "../components/table/TableRowIcon"
import {showMetrics} from "../actions/nk"
import {useDispatch} from "react-redux"
import React, {useCallback} from "react"

export function MetricsItem({process}: { process: $TodoType }) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const clickHandler = useCallback(
    () => dispatch(showMetrics(process.name)),
    [process],
  )
  return (
    <TableRowIcon glyph={"stats"} title={t("tableRowIcon-show-metrics","Show metrics")} onClick={clickHandler}/>
  )
}
