import React from "react"
import {useTranslation} from "react-i18next"
import "../stylesheets/togglePanel.styl"
import {ReactComponent as LeftIcon} from "../assets/img/arrows/arrow-left.svg"
import {ReactComponent as RightIcon} from "../assets/img/arrows/arrow-right.svg"
import {cx} from "@emotion/css"

interface Props {
  isOpened: boolean,
  onToggle: () => void,
  type: "RIGHT" | "LEFT",
}

export default function TogglePanel(props: Props): JSX.Element {
  const {t} = useTranslation()
  const {isOpened, onToggle, type} = props
  const left = type === "LEFT" ? isOpened : !isOpened
  const title = type === "LEFT" ?
    t("panel.toggle.left", "toggle left panel") :
    t("panel.toggle.right", "toggle right panel")
  return (
    <div title={title} className={cx("togglePanel", type, {"is-opened": isOpened})} onClick={onToggle}>
      {left ? <LeftIcon/> : <RightIcon/>}
    </div>
  )
}
