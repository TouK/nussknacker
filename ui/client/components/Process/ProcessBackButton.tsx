import React from "react"
import {ProcessLink} from "../../containers/processLink"
import {ReactComponent as ProcessBackIcon} from "../../assets/img/arrows/back-process.svg"
import {useTranslation} from "react-i18next"
import styles from "./ProcessBackButton.styl"

type Props = {
  processId: string,
}

export default function ProcessBackButton(props: Props) {
  const {t} = useTranslation()
  const {processId} = props

  return (
    <ProcessLink processId={processId} className={styles.button} title={t("processBackButton.title", "Go back to {{processId}} graph page", {processId})}>
      <ProcessBackIcon className={styles.icon}/>
      <span className={styles.text}>
        {t("processBackButton.text", "back to {{processId}}", {processId})}
      </span>
    </ProcessLink>
  )
}
