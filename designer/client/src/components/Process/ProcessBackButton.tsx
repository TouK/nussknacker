import React, {useMemo} from "react"
import {ProcessLink} from "../../containers/processLink"
import {ReactComponent as ProcessBackIcon} from "../../assets/img/arrows/back-process.svg"
import {useTranslation} from "react-i18next"
import styles from "./ProcessBackButton.styl"
import {matchPath, useLocation} from "react-router-dom"
import {MetricsBasePath} from "../../containers/paths"

export default function ProcessBackButton() {
  const {t} = useTranslation()
  const {pathname} = useLocation()
  const processId = useMemo(() => {
    const match = matchPath(`${MetricsBasePath}/:processId`, pathname)
    return match?.params?.processId
  }, [pathname])

  if (!processId) {
    return null
  }

  return (
    <ProcessLink processId={processId} className={styles.button} title={t("processBackButton.title", "Go back to {{processId}} graph page", {processId})}>
      <ProcessBackIcon className={styles.icon}/>
      <span className={styles.text}>
        {t("processBackButton.text", "back to {{processId}}", {processId})}
      </span>
    </ProcessLink>
  )
}
