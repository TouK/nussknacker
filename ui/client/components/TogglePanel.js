import cn from "classnames"
import PropTypes from "prop-types"
import React from "react"
import {useTranslation} from "react-i18next"
import "../stylesheets/togglePanel.styl"

import SvgDiv from "./SvgDiv"

export default function TogglePanel(props) {
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

TogglePanel.propTypes = {
  type: PropTypes.oneOf(["RIGHT", "LEFT"]).isRequired,
  isOpened: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
}
