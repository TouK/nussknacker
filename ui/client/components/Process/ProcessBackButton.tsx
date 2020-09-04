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
    <ProcessLink processId={processId} className={styles.button} >
      <ProcessBackIcon className={styles.icon}/>
      <span className={styles.text}>
        {t("processBackButton.text", "Back to process {{processId}}", {processId})}
      </span>
    </ProcessLink>
  )
}
