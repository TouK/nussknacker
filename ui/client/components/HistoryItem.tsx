import {css, cx} from "emotion"
import React, {useMemo} from "react"
import {useTranslation} from "react-i18next"
import styles from "../stylesheets/processHistory.styl"
import Date from "./common/Date"
import {ReactComponent as Badge} from "./deployed.svg"
import {ActionType, ProcessVersionType} from "./Process/types"

type HistoryItemProps = {
  isLatest?: boolean,
  isDeployed?: boolean,
  version: ProcessVersionType,
  onClick: (version: ProcessVersionType) => void,
  type: VersionType,
}

export enum VersionType {
  current,
  past,
  future,
}

const mapVersionToClassName = (v: VersionType): string => {
  switch (v) {
    case VersionType.current:
      return styles.current
    case VersionType.past:
      return styles.past
    case VersionType.future:
      return styles.future
  }
}

const HDate = ({date}: {date: Date}) => <small><i><Date date={date}/></i></small>

export function HistoryItem({
  onClick,
  version,
  type,
  isLatest,
  isDeployed,
}: HistoryItemProps): JSX.Element {
  const {t} = useTranslation()
  const {user, createDate, processVersionId, actions} = version
  const className = useMemo(() => mapVersionToClassName(type), [type])

  const flex = css({display: "flex", justifyContent: "space-between", alignItems: "flex-start"})
  const badgeStyles = css({height: "1.2em", margin: "0 1.2em"})
  return (
    <li className={cx(className, flex)} onClick={() => onClick(version)}>
      <div>
        {`v${processVersionId}`} | {user}
        {isLatest && !isDeployed && (
          <small>
            <span
              title={t("processHistory.lastVersionIsNotDeployed", "Last version is not deployed")}
              className="glyphicon glyphicon-warning-sign"
            />
          </small>
        )}
        <br/>
        <HDate date={createDate}/>
        <br/>
        {isDeployed && <HDate date={actions.find(a => a.action === ActionType.Deploy)?.performedAt}/>}
      </div>
      {isDeployed && <Badge className={badgeStyles}/>}
    </li>
  )
}
