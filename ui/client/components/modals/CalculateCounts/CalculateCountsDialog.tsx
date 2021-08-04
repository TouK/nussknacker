import moment from "moment"
import React, {useCallback, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {fetchAndDisplayProcessCounts} from "../../../actions/nk"
import {getProcessId} from "../../../reducers/selectors/graph"
import "../../../stylesheets/visualization.styl"
import Dialogs from "../Dialogs"
import GenericModalDialog from "../GenericModalDialog"
import {Picker, PickerInput} from "./Picker"
import {CountsRanges} from "./CountsRanges"

interface State {
  from: PickerInput,
  to: PickerInput,
}

const initState = (): State => {
  return {
    from: moment().startOf("day"),
    to: moment().endOf("day"),
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

  const setFrom = useCallback((from: PickerInput) => {
    setState(current => ({...current, from}))
  }, [])

  const setTo = useCallback((to: PickerInput) => {
    setState(current => ({...current, to}))
  }, [])

  const setRange = useCallback((value: [PickerInput, PickerInput]) => {
    const [from, to] = value
    setState(current => ({...current, from, to}))
  }, [])

  const isValid = (input: PickerInput) => moment(input).isValid()
  return (
    <GenericModalDialog
      init={init}
      confirm={confirm}
      type={Dialogs.types.calculateCounts}
      okBtnConfig={{disabled: !(isValid(from) && isValid(to)) }}
      header={t("calculateCounts.title", "counts")}
    >
      <Picker
        label={t("calculateCounts.processCountsFrom", "Scenario counts from")}
        onChange={setFrom}
        value={from}
      />
      <Picker
        label={t("calculateCounts.processCountsTo", "Scenario counts to")}
        onChange={setTo}
        value={to}
      />
      <CountsRanges
        label={t("calculateCounts.quickRanges", "Quick ranges")}
        onChange={setRange}
      />
    </GenericModalDialog>
  )
}

export default CalculateCountsDialog
