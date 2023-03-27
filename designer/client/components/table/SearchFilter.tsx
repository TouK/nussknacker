import {css, cx} from "@emotion/css"
import React from "react"
import {useNkTheme} from "../../containers/theme"
import {ReactComponent as SearchSvg} from "../../assets/img/search.svg"
import {ReactComponent as DeleteSvg} from "../../assets/img/toolbarButtons/delete.svg"

const flex = css({
  width: 0, // edge 18. why? because! 🙃
  flex: 1,
})

export function SearchIcon(props: { isEmpty?: boolean }): JSX.Element {
  const {theme} = useNkTheme()
  return (
    <SearchSvg
      className={cx(flex, css({
        ".icon-fill": {
          fill: props.isEmpty ? theme.colors.secondaryColor : theme.colors.accent,
        },
      }))}
    />
  )
}

export function ClearIcon(): JSX.Element {
  const {theme} = useNkTheme()
  return (
    <DeleteSvg
      className={cx(flex, css({
        path: {
          fill: theme.colors.mutedColor,
        },
      }))}
    />
  )
}
