import {css} from "emotion"
import moment, {Moment} from "moment"
import React, {useEffect, useMemo, useState} from "react"
import {TFunction, useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {useFeatureFlags} from "../../../common/featureFlags"
import HttpService from "../../../http/HttpService"
import {getProcessId} from "../../../reducers/selectors/graph"
import {CountsRangesButtons, Range} from "./CountsRangesButtons"

function predefinedRanges(t: TFunction<string>): Range[] {

  const forDay = (name: string, moment: () => Moment): Range => ({
    name: name,
    from: () => moment().startOf("day"),
    to: () => moment().endOf("day"),
  })

  return [
    {
      name: t("calculateCounts.range.lastHour", "Last hour"),
      from: () => moment().subtract(1, "hour"),
      to: () => moment(),
    },
    forDay(t("calculateCounts.range.today", "Today"), () => moment()),
    forDay(t("calculateCounts.range.yesterday", "Yesterday"), () => moment().subtract(1, "day")),
    forDay(t("calculateCounts.range.dayBeforeYesterday", "Day before yesterday"), () => moment().subtract(2, "day")),
    forDay(t("calculateCounts.range.thisDayLastWeek", "This day last week"), () => moment().subtract(7, "day")),
  ]
}

interface RangesProps {
  label: string,
  onChange: (value: [Moment, Moment]) => void,
}

function useDeployHistory(processId: string): Range[] {
  const {t} = useTranslation()
  const [deploys, setDeploys] = useState<Range[]>([])
  const {showDeploymentsInCounts} = useFeatureFlags()
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

const rangesStyle = css({
  display: "flex",
  flexWrap: "wrap",
  justifyContent: "center",
  maxWidth: 600,
  margin: "0 -10px",
  button: {
    margin: "10px",
  },
})

export function CountsRanges({label, onChange}: RangesProps): JSX.Element {
  const {t} = useTranslation<string>()
  const processId = useSelector(getProcessId)
  const deploys = useDeployHistory(processId)
  const ranges = useMemo(() => [...predefinedRanges(t), ...deploys], [t, deploys])

  return (
    <>
      <p>{label}</p>
      <div className={rangesStyle}>
        <CountsRangesButtons ranges={ranges} onChange={onChange} limit={6}/>
      </div>
    </>
  )
}
