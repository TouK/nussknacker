import React from "react"
import {useTranslation} from "react-i18next"
import styles from "../stylesheets/processHistory.styl"
import Date from "./common/Date"
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

export function HistoryItem({
  onClick,
  version,
  type,
  isLatest,
  isDeployed,
}: HistoryItemProps): JSX.Element {
  const {t} = useTranslation()
  const className = mapVersionToClassName(type)
  return (
    <li className={className} onClick={() => onClick(version)}>
      {`v${version.processVersionId}`} | {version.user}
      {isLatest && !isDeployed && (
        <small><span
          title={t("processHistory.lastVersionIsNotDeployed", "Last version is not deployed")}
          className="glyphicon glyphicon-warning-sign"
        /></small>
      )}
      <br/>
      <small><i><Date date={version.createDate}/></i></small>
      <br/>
      {isDeployed && (
        <small>
          <i><Date date={version.actions.find(a => a.action === ActionType.Deploy)?.performedAt}/></i>
          <span className="label label-info">
            {t("processHistory.lastDeployed", "Last deployed")}
          </span>
        </small>
      )}
    </li>
  )
}
