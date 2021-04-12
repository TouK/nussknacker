import React, {PropsWithChildren, createContext} from "react"
import styles from "./ToolbarButtons.styl"
import cn from "classnames"

export enum ButtonsVariant {
  small = "small",
  label = "label"
}

type Props = {
  variant?: ButtonsVariant,
}

export const ToolbarButtonsContext = createContext<{variant: ButtonsVariant}>({variant: ButtonsVariant.label})

export function ToolbarButtons(props: PropsWithChildren<Props>): JSX.Element {
  const {variant = ButtonsVariant.label} = props

  return (
    <ToolbarButtonsContext.Provider value={{variant}}>
      <div className={cn(styles.list)}>
        {props.children}
      </div>
    </ToolbarButtonsContext.Provider>
  )
}
