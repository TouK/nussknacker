import {ThemeProvider} from "@mui/material/styles"
import ScopedCssBaseline from "@mui/material/ScopedCssBaseline"
import React from "react"
import {useMuiTheme} from "./useMuiTheme"

export const MuiThemeProvider: React.FC<unknown> = ({children}) => {
  const muiTheme = useMuiTheme()

  return (
    <ThemeProvider theme={muiTheme}>
      <ScopedCssBaseline style={{flex: 1}}>
        {children}
      </ScopedCssBaseline>
    </ThemeProvider>
  )
}
