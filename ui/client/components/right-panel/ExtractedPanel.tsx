import {PanelConfig} from "./PanelConfig"
import {ButtonWithIcon} from "./ButtonWithIcon"
import cn from "classnames"
import React from "react"
import {RightPanel} from "./RightPanel"

export function ExtractedPanel({buttons, panelName, isHidden}: PanelConfig) {
  const visibleButtons = buttons.filter(button => !button.isHidden)

  return (
    <RightPanel title={panelName} isHidden={isHidden || !visibleButtons.length}>
      {visibleButtons.map(({name, title, className, isHidden, ...props}) => (
        <ButtonWithIcon
          {...props}
          key={name}
          name={name}
          title={title || name}
          className={cn(className, "espButton", "right-panel")}
        />
      ))}
    </RightPanel>
  )
}
