import React, {PropsWithChildren, createContext} from "react"
import styles from "./ToolbarButtons.styl"
import cn from "classnames"

type Props = {
  small?: boolean,
}

export const ToolbarButtonsContext = createContext<{ small: boolean }>({small: false})

export function ToolbarButtons(props: PropsWithChildren<Props>) {
  const {small} = props

  return (
    <ToolbarButtonsContext.Provider value={{small}}>
      <div className={cn(styles.list)}>
        {props.children}
      </div>
    </ToolbarButtonsContext.Provider>
  )
}
