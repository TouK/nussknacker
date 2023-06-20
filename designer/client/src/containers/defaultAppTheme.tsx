/* eslint-disable i18next/no-literal-string */
import React from "react";
import vars from "../stylesheets/_variables.styl";
import { tintPrimary } from "./theme";

type DeepPartial<T> = {
    [P in keyof T]?: DeepPartial<T[P]>;
};

const {
    borderRadius,
    formControllHeight,
    fontSize,
    primary,
    warningColor: warning,
    errorColor: error,
    okColor: ok,
    sucessColor: sucess,
} = vars;

export const defaultAppTheme = {
    themeClass: "",
    borderRadius: parseFloat(borderRadius),
    colors: {
        ...tintPrimary(primary),
        danger: "#DE350B",
        dangerLight: "#FFBDAD",
        neutral0: "hsl(0, 0%, 100%)",
        neutral5: "hsl(0, 0%, 95%)",
        neutral10: "hsl(0, 0%, 90%)",
        neutral20: "hsl(0, 0%, 80%)",
        neutral30: "hsl(0, 0%, 70%)",
        neutral40: "hsl(0, 0%, 60%)",
        neutral50: "hsl(0, 0%, 50%)",
        neutral60: "hsl(0, 0%, 40%)",
        neutral70: "hsl(0, 0%, 30%)",
        neutral80: "hsl(0, 0%, 20%)",
        neutral90: "hsl(0, 0%, 10%)",

        borderColor: "hsl(0, 0%, 30%)",
        canvasBackground: "hsl(0, 0%, 100%)",
        primaryBackground: "hsl(0, 0%, 100%)",
        secondaryBackground: "hsl(0, 0%, 100%)",
        primaryColor: "hsl(0, 0%, 10%)",
        secondaryColor: "hsl(0, 0%, 30%)",
        mutedColor: "hsl(0, 0%, 50%)",

        focusColor: primary,
        evenBackground: "hsl(0, 0%, 80%)",

        selectedValue: primary,
        accent: primary,
        warning,
        error,
        ok,
        sucess,
        warn: warning,
        info: primary,
    },
    spacing: {
        controlHeight: parseFloat(formControllHeight),
        baseUnit: 4,
    },
    fontSize: parseFloat(fontSize),
};
