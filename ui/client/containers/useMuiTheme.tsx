import {colors} from "@material-ui/core"
import {createTheme, Theme as MuiTheme} from "@material-ui/core/styles"
import {defaultsDeep} from "lodash"
import {useMemo} from "react"
import {useNkTheme} from "./theme"

const paletteTheme = createTheme({
  palette: {
    type: `dark`,
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

const overridesTheme = createTheme(
  {
    overrides: {
      MuiTableBody: {
        root: {
          backgroundColor: colors.grey["700"],
        },
      },
      MuiTableCell: {
        stickyHeader: {
          backgroundColor: colors.grey["800"],
        },
        head: {
          color: paletteTheme.palette.text.secondary,
        },
      },
    },
  },
  paletteTheme,
)

// translate emotion (nk) theme to mui theme
export function useMuiTheme(): MuiTheme {
  const {theme} = useNkTheme()

  const isDark = useMemo(
    () => theme.themeClass.toLocaleLowerCase().includes("dark"),
    [theme.themeClass],
  )

  return useMemo(
    () => defaultsDeep(isDark ? overridesTheme : {}, theme),
    [isDark, theme],
  )
}
