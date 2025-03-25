import { Typography, useTheme } from "@mui/material";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import Creatable from "react-select/creatable";

import { useUserSettings } from "../../common/userSettings";
import type { UserSettings } from "../../reducers/userSettings";
import type { ToolbarPanelProps } from "../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";

export function UserSettingsPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();
    const theme = useTheme();
    const [settings, , reset] = useUserSettings();

    const lightMode = settings["debug.lightTheme"];
    useEffect(() => {
        theme.setMode(lightMode ? "light" : "dark");
    }, [theme, lightMode]);

    const value = Object.entries(settings)
        .map(([label, value]) => ({ label, value }))
        .sort((a, b) => b.label.localeCompare(a.label));
    return (
        <ToolbarWrapper {...props} title={t("panels.userSettings.title", "User settings")} color={"#254706"}>
            <Creatable
                isMulti
                value={value}
                getOptionValue={(option) => `${option.label}_${option.value}`}
                onChange={(values) => reset(values?.reduce((current, { label, value }) => ({ ...current, [label]: !!value }), {}))}
                isValidNewOption={(inputValue) => /^[^_]/.test(inputValue)}
                styles={{
                    multiValue: (base) => ({
                        ...base,
                        width: "100%",
                        backgroundColor: theme.palette.background.default,
                        color: theme.palette.getContrastText(theme.palette.background.default),
                        cursor: "pointer",
                    }),
                    multiValueLabel: (base) => ({
                        ...base,
                        width: "100%",
                        color: "inherit",
                    }),
                    control: (base) => ({
                        ...base,
                        padding: 0,
                        border: "none",
                        backgroundColor: "transparent",
                        outline: 0,
                        borderRadius: 0,
                        boxShadow: "none",
                    }),
                    input: (base) => ({
                        ...base,
                        color: theme.palette.text.primary,
                        outline: 0,
                    }),
                    valueContainer: (base) => ({ ...base, padding: 4, flexWrap: "wrap-reverse" }),
                }}
                components={{
                    DropdownIndicator: null,
                    ClearIndicator: null,
                    Menu,
                    MultiValueLabel,
                }}
            />
        </ToolbarWrapper>
    );
}

const Menu = () => <></>;

interface MultiValueLabelProps {
    data: { label: keyof UserSettings; value: unknown };
    innerProps: { className?: string };
}

const MultiValueLabel = ({ data, innerProps }: MultiValueLabelProps) => {
    const [, toggle] = useUserSettings();

    return (
        <Typography variant={"subtitle2"} onClick={() => toggle([data.label])} className={innerProps.className}>
            {data.value ? "✅" : "⛔️"} {data.label}
        </Typography>
    );
};
