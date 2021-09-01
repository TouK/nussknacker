import {DefaultContent, DefaultContentProps} from "@touk/window-manager"
import {css} from "emotion"
import React, {PropsWithChildren, useMemo} from "react"
import {useNkTheme} from "../containers/theme"
import {LaddaButton} from "./LaddaButton"

const HeaderPlaceholder = () => <header>{/*grid placeholder*/}</header>

export function PromptContent(props: PropsWithChildren<DefaultContentProps>): JSX.Element {
  const {theme} = useNkTheme()
  const classnames = useMemo(() => {
    const content = css({
      paddingBottom: theme.spacing.baseUnit,
      paddingTop: theme.spacing.baseUnit * 2,
      paddingLeft: theme.spacing.baseUnit * 6,
      paddingRight: theme.spacing.baseUnit * 6,
    })
    return {...props.classnames, content}
  }, [props.classnames, theme.spacing.baseUnit])

  const components = useMemo(() => ({
    FooterButton: LaddaButton,
    ...props.components,
    Header: HeaderPlaceholder,
  }), [props.components])

  return (
    <DefaultContent
      backgroundDrag
      {...props}
      classnames={classnames}
      components={components}
    />
  )
}

