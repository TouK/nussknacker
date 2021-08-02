import {createGenerateClassName, StylesProvider} from "@material-ui/core"
import {ThemeProvider} from "@material-ui/styles"
import React, {useMemo} from "react"
import {useMuiTheme} from "./useMuiTheme"

export const MuiThemeProvider: React.FC<{seed: string}> = ({seed, children}) => {
  const generateClassName = useMemo(
    () => createGenerateClassName({seed}),
    [seed],
  )

  const muiTheme = useMuiTheme()

  return (
    <StylesProvider injectFirst generateClassName={generateClassName}>
      <ThemeProvider theme={muiTheme}>
        {children}
      </ThemeProvider>
    </StylesProvider>
  )
}
