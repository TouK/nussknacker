/* eslint-disable i18next/no-literal-string */
import React from "react"
import {jest} from "@jest/globals"

export const tintPrimary = jest.fn()

export const NkThemeProvider = ({children}) => <>{children}</>

export const useNkTheme = () => ({theme: {}, withFocus: "withFocus"})
