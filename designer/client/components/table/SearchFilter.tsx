import {css, cx} from "@emotion/css"
import React from "react"
import {useNkTheme} from "../../containers/theme"
import SvgDiv from "../SvgDiv"
import searchIconStyles from "./searchIcon.styl"

export function AddonIcon(props: { className?: string, svg: string }): JSX.Element {
  return <SvgDiv className={cx(searchIconStyles.icon, props.className)} svgFile={props.svg}/>
}

export function SearchIcon(props: { isEmpty?: boolean }): JSX.Element {
  const {theme} = useNkTheme()
  const styles = css({
    svg: {
      ".icon-fill": {
        fill: props.isEmpty ? theme.colors.secondaryColor : theme.colors.accent,
      },
    },
  })
  return <AddonIcon svg="search.svg" className={styles}/>
}
