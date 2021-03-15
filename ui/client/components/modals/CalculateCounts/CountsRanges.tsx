import moment, {Moment} from "moment"
import React, {CSSProperties, useEffect, useMemo, useState} from "react"
import {TFunction, useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {useFeatureFlags} from "../../../common/featureFlags"
import HttpService from "../../../http/HttpService"
import {getProcessId} from "../../../reducers/selectors/graph"
import {CountsRangesButtons, Range} from "./CountsRangesButtons"

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

function useDeployHistory(processId: string): Range[] {
  const {t} = useTranslation()
  const [deploys, setDeploys] = useState<Range[]>([])
  const [{showDeploymentsInCounts}] = useFeatureFlags()
  useEffect(() => {
    if (!showDeploymentsInCounts) {
      setDeploys([])
    } else {
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
    }
  }, [showDeploymentsInCounts, t, processId])

  return deploys
}

const rangesStyle: CSSProperties = {
  display: "flex",
  flexWrap: "wrap",
  justifyContent: "center",
  padding: "4%",
}

export function CountsRanges({label, onChange}: RangesProps): JSX.Element {
  const {t} = useTranslation()
  const processId = useSelector(getProcessId)
  const deploys = useDeployHistory(processId)
  const ranges = useMemo(() => [...predefinedRanges(t), ...deploys], [t, deploys])

  return (
    <>
      <p>{label}</p>

      <div style={rangesStyle}>
        <CountsRangesButtons ranges={ranges} onChange={onChange} limit={6}/>
      </div>
    </>
  )
}
