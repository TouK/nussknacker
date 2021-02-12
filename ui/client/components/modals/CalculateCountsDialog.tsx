/* eslint-disable i18next/no-literal-string */
import moment, {MomentInput} from "moment"
import React, {useCallback, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {fetchAndDisplayProcessCounts} from "../../actions/nk"
import {getProcessId} from "../../reducers/selectors/graph"
import "../../stylesheets/visualization.styl"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"
import {Picker} from "./Picker"
import {Ranges} from "./Ranges"

interface State {
  from: Date,
  to: Date,
}

const initState = (): State => {
  const nowMidnight = moment().startOf("day")
  const yesterdayMidnight = moment().subtract(1, "days").startOf("day")
  return {
    from: yesterdayMidnight.toDate(),
    to: nowMidnight.toDate(),
  }
}

function CalculateCountsDialog(): JSX.Element {
  const {t} = useTranslation()
  const [{from, to}, setState] = useState(initState)
  const processId = useSelector(getProcessId)
  const dispatch = useDispatch()

  const init = useCallback(() => setState(initState), [])

  const confirm = useCallback(async () => {
    await dispatch(fetchAndDisplayProcessCounts(processId, moment(from), moment(to)))
  }, [processId, from, to])

  const setFrom = useCallback((date: MomentInput) => {
    const from = moment(date).toDate()
    setState(current => ({...current, from}))
  }, [])

  const setTo = useCallback((date: MomentInput) => {
    const to = moment(date).toDate()
    setState(current => ({...current, to}))
  }, [])

  const setRange = useCallback((value: [MomentInput, MomentInput]) => {
    const [from, to] = value.map(v => moment(v).toDate())
    setState(current => ({...current, from, to}))
  }, [])

  return (
    <GenericModalDialog
      init={init}
      confirm={confirm}
      type={Dialogs.types.calculateCounts}
      header={t("calculateCounts.title", "counts")}
    >
      <Picker
        label={t("calculateCounts.processCountsFrom", "Process counts from")}
        onChange={setFrom}
        value={from}
      />
      <Picker
        label={t("calculateCounts.processCountsTo", "Process counts to")}
        onChange={setTo}
        value={to}
      />
      <Ranges
        label={t("calculateCounts.quickRanges", "Quick ranges")}
        onChange={setRange}
      />
    </GenericModalDialog>
  )
}

export default CalculateCountsDialog
