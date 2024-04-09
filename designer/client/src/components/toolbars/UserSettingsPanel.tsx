import React from "react";
import { Typography, useTheme } from "@mui/material";
import { useTranslation } from "react-i18next";
import Creatable from "react-select/creatable";
import { useUserSettings } from "../../common/userSettings";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";

export function UserSettingsPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();
    const theme = useTheme();
    const [settings, , reset] = useUserSettings();
    const value = Object.entries(settings).map(([label, value]) => ({ label, value }));
    return (
        <ToolbarWrapper {...props} title={t("panels.userSettings.title", "🧪 User settings")}>
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
                        backgroundColor: theme.palette.success.dark,
                        cursor: "pointer",
                    }),
                    multiValueLabel: (base) => ({ ...base, width: "100%", fontWeight: "bold", color: theme.palette.text.secondary }),
                    control: (base) => ({ ...base, padding: 0, border: "none", backgroundColor: theme.palette.background.paper }),
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
    data: { label: string; value: unknown };
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
