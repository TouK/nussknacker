import React, {PropsWithChildren} from "react"
import {useTranslation} from "react-i18next"
import {CollapsibleToolbar, CollapsibleToolbarProps} from "../toolbarComponents/CollapsibleToolbar"
import {ButtonsVariant, ToolbarButtons} from "../toolbarComponents/ToolbarButtons"

export type ToolbarPanelProps = PropsWithChildren<{
  id: string,
  title?: string,
  buttonsVariant?: ButtonsVariant,
}>

export function DefaultToolbarPanel(props: ToolbarPanelProps & CollapsibleToolbarProps): JSX.Element {
  const {t} = useTranslation()
  const {children, title, id, buttonsVariant, ...passProps} = props
  return (
    /* i18next-extract-disable-line */
    <CollapsibleToolbar id={id} title={t(`panels.${id}.title`, title || id)} {...passProps}>
      <ToolbarButtons variant={buttonsVariant}>
        {children}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}
