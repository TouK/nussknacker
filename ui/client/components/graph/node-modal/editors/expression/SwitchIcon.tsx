import classNames from "classnames"
import React, {MouseEventHandler} from "react"
import {useTranslation} from "react-i18next"
import * as LoaderUtils from "../../../../../common/LoaderUtils"

export default function SwitchIcon(props: Props) {
  const {switchable, readOnly, hint, onClick, displayRawEditor, shouldShowSwitch} = props

  if (!shouldShowSwitch) {
    return null
  }

  const {t} = useTranslation()

  const title = readOnly ? t("Switching to basic mode is disabled. You are in read-only mode") : hint

  const className = classNames([
    "inlined",
    "switch-icon",
    displayRawEditor && "active",
    readOnly && "read-only",
  ])

  return (
    <button
      className={className}
      onClick={onClick}
      disabled={!switchable || readOnly}
      title={title}
      >
      <div dangerouslySetInnerHTML={{__html: LoaderUtils.loadSvgContent("buttons/switch.svg")}}/>
    </button>
  )
}

type Props = {
  onClick: MouseEventHandler<HTMLButtonElement>,
  hint: string,
  switchable?: boolean,
  shouldShowSwitch?: boolean,
  displayRawEditor?: boolean,
  readOnly?: boolean,
}
