import {createTheme, Theme as MuiTheme} from "@mui/material/styles"
import {defaultsDeep} from "lodash"
import {useMemo} from "react"
import {useNkTheme} from "./theme"
import {Theme} from "@emotion/react"

// translate emotion (nk) theme to mui theme
export function useMuiTheme(): MuiTheme & Theme {
  const {theme} = useNkTheme()

  const isDark = useMemo(
    () => theme.themeClass.toLowerCase().includes("dark"),
    [theme.themeClass],
  )

  return useMemo(
    () => defaultsDeep(createTheme(createTheme({
      palette: {
        mode: isDark ? "dark" : "light",
        primary: {
          main: `#a9e074`,
        },
        secondary: {
          main: `#762976`,
        },
        error: {
          main: `#F25C6E`,
        },
        success: {
          main: `#5CB85C`,
          contrastText: `#FFFFFF`,
        },
        background: {
          paper: theme.colors.primaryBackground,
          default: theme.colors.canvasBackground,
        },
      },
      components: {
        MuiSwitch: {
          styleOverrides: {
            input: {
              margin: 0,
            },
          },
        },
      },
    })), theme),
    [isDark, theme],
  )
}
