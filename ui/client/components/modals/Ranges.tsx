import moment, {Moment} from "moment"
import React, {useEffect, useMemo, useState} from "react"
import {TFunction, useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import HttpService from "../../http/HttpService"
import {getProcessId} from "../../reducers/selectors/graph"
import {Range, RangesButtons} from "./RangesButtons"

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

interface RangesProps {
  label: string,
  onChange: (value: [Moment, Moment]) => void,
}

export function Ranges({label, onChange}: RangesProps): JSX.Element {
  const {t} = useTranslation()
  const processId = useSelector(getProcessId)

  const [deploys, setDeploys] = useState<Range[]>([])
  useEffect(() => {
    HttpService.fetchProcessesDeployments(processId)
      .then(dates => dates.map((current, i, all) => {
        const from = current
        const to = all[i - 1]
        return {
          from: () => moment(from),
          to: () => to ? moment(to) : moment().add(1, "day").startOf("day"),
          name: i ?
            t("calculateCounts.range.prevDeploy", "Previous deploy #{{i}} ({{date}})", {i: all.length - i, date: from}) :
            t("calculateCounts.range.lastDeploy", "Latest deploy"),
        }
      }))
      .then(setDeploys)
  }, [t, processId])

  const ranges = useMemo(() => predefinedRanges(t), [t])
  return (
    <>
      <p>{label}</p>

      <div style={{display: "flex", flexWrap: "wrap", justifyContent: "center", padding: "4%"}}>
        <RangesButtons ranges={[...ranges, ...deploys]} onChange={onChange} limit={6}/>
      </div>
    </>
  )
}
