import {createTheme, Theme as MuiTheme} from "@material-ui/core/styles"
import {defaultsDeep} from "lodash"
import {useMemo} from "react"
import {useNkTheme} from "./theme"

// translate emotion (nk) theme to mui theme
export function useMuiTheme(): MuiTheme {
  const {theme} = useNkTheme()

  const isDark = useMemo(
    () => theme.themeClass.toLocaleLowerCase().includes("dark"),
    [theme.themeClass],
  )

  return useMemo(
    () => defaultsDeep(createTheme({
      palette: {
        type: isDark ? "dark" : "light",
      },
    }), theme),
    [isDark, theme],
  )
}
