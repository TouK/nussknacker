import {ThemeProvider} from "@mui/material/styles"
import React from "react"
import {useMuiTheme} from "./useMuiTheme"

export const MuiThemeProvider: React.FC<unknown> = ({children}) => {
  const muiTheme = useMuiTheme()

  return (
    <ThemeProvider theme={muiTheme}>
      {children}
    </ThemeProvider>
  )
}
