import cn from "classnames"
import React from "react"
import {useTranslation} from "react-i18next"
import "../stylesheets/togglePanel.styl"

import SvgDiv from "./SvgDiv"

interface Props {
  isOpened: boolean,
  onToggle: () => void,
  type: "RIGHT" | "LEFT",
}

export default function TogglePanel(props: Props): JSX.Element {
  const {t} = useTranslation()
  const {isOpened, onToggle, type} = props
  const left = type === "LEFT" ? isOpened : !isOpened
  const iconFile = `arrows/arrow-${left ? "left" : "right"}.svg`
  const title = type === "LEFT" ?
    t("panel.toggle.left", "toggle left panel") :
    t("panel.toggle.right", "toggle right panel")
  return (
    <SvgDiv title={title} className={cn("togglePanel", type, {"is-opened": isOpened})} onClick={onToggle} svgFile={iconFile}/>
  )
}
