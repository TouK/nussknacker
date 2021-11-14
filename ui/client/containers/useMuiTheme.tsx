import {createTheme, Theme as MuiTheme} from "@mui/material/styles"
import {defaultsDeep} from "lodash"
import {useMemo} from "react"
import {useNkTheme} from "./theme"
import {Theme} from "@emotion/react"

const paletteTheme = createTheme({
  palette: {
    mode: `dark`,
    primary: {
      main: `#93BB6C`,
    },
    secondary: {
      main: `#3047F0`,
    },
    error: {
      main: `#F25C6E`,
    },
    success: {
      main: `#5CB85C`,
      contrastText: `#FFFFFF`,
    },
    background: {
      default: `#CCCCCC`,
    },
  },
})

// translate emotion (nk) theme to mui theme
export function useMuiTheme(): MuiTheme & Theme {
  const {theme} = useNkTheme()

  const isDark = useMemo(
    () => theme.themeClass.toLowerCase().includes("dark"),
    [theme.themeClass],
  )

  return useMemo(
    () => defaultsDeep(isDark ? paletteTheme : {}, theme),
    [isDark, theme],
  )
}
