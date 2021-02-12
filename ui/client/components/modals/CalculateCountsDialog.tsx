/* eslint-disable i18next/no-literal-string */
import moment, {Moment, MomentInput} from "moment"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import DateTimePicker from "react-datetime"
import {TFunction, useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {fetchAndDisplayProcessCounts} from "../../actions/nk"
import HttpService from "../../http/HttpService"
import {getProcessId} from "../../reducers/selectors/graph"
import "../../stylesheets/visualization.styl"
import {ButtonWithFocus} from "../withFocus"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"

const datePickerStyle = {
  className: "node-input",
}

const dateFormat = "YYYY-MM-DD"
const timeFormat = "HH:mm:ss"

interface Range {
  name: string,
  from: () => Moment,
  to: () => Moment,
}

function predefinedRanges(t: TFunction<string>): Range[] {
  return [
    {
      name: t("calculateCounts.range.lastHour", "Last hour"),
      from: () => moment().subtract(1, "hour").startOf("minute"),
      to: () => moment().add(1, "day").startOf("day"),
    },
    {
      name: t("calculateCounts.range.today", "Today"),
      from: () => moment().startOf("day"),
      to: () => moment().add(1, "day").startOf("day"),
    },
    {
      name: t("calculateCounts.range.yesterday", "Yesterday"),
      from: () => moment().subtract(1, "day").startOf("day"),
      to: () => moment().startOf("day"),
    },
    {
      name: t("calculateCounts.range.dayBeforeYesterday", "Day before yesterday"),
      from: () => moment().subtract(2, "days").startOf("day"),
      to: () => moment().subtract(1, "day").startOf("day"),
    },
    {
      name: t("calculateCounts.range.thisDayLastWeek", "This day last week"),
      from: () => moment().subtract(8, "days").startOf("day"),
      to: () => moment().subtract(7, "days").startOf("day"),
    },
  ]
}

type PickerProps = {label: string, onChange: (date: MomentInput) => void, value: Date}
const Picker = ({label, onChange, value}: PickerProps): JSX.Element => (
  <>
    <p>{label}</p>
    <div className="datePickerContainer">
      <DateTimePicker
        dateFormat={dateFormat}
        timeFormat={timeFormat}
        inputProps={datePickerStyle}
        onChange={onChange}
        value={value}
      />
    </div>
  </>
)

type RangeButtonProps = {range: Range, onChange: (value: [Moment, Moment]) => void}
const RangeButton = ({range, onChange}: RangeButtonProps): JSX.Element => {
  const {from, to, name} = range
  const onClick = useCallback(
    () => onChange([from(), to()]),
    [from, to],
  )
  return (
    <ButtonWithFocus type="button" title={name} className="predefinedRangeButton" onClick={onClick}>
      {name}
    </ButtonWithFocus>
  )
}

type RangesProps = {label: string, onChange: (value: [Moment, Moment]) => void}
const Ranges = ({label, onChange}: RangesProps): JSX.Element => {
  const {t} = useTranslation()
  const processId = useSelector(getProcessId)

  const [[lastDeploy, ...deploys], setDeploys] = useState<Range[]>([])
  useEffect(() => {
    HttpService.fetchProcessesDeployments(processId)
      .then(dates => dates.map((current, i, all) => {
        const from = current
        const to = all[i - 1]
        return {
          from: () => moment(from),
          to: () => to ? moment(to) : moment().add(1, "day").startOf("day"),
          name: i ?
            t("calculateCounts.range.prevDeploy", "Deploy ({{i}})", {i: all.length - i, date: moment(from).toLocaleString()}) :
            t("calculateCounts.range.lastDeploy", "Latest deploy"),
        }
      }))
      .then(setDeploys)
  }, [t, processId])

  const ranges = useMemo(() => predefinedRanges(t), [t])
  return (
    <>
      <p>{label}</p>
      {ranges.map(range => (
        <RangeButton key={range.name} range={range} onChange={onChange}/>
      ))}

      {lastDeploy && <RangeButton range={lastDeploy} onChange={onChange}/>}

      {deploys.map(range => (
        <RangeButton key={range.name} range={range} onChange={onChange}/>
      ))}
    </>
  )
}

type State = {from: Date, to: Date}

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
  const [{from, to}, setState] = useState<State>(initState)
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
      // header={t("calculateCounts.title", "counts")}
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
