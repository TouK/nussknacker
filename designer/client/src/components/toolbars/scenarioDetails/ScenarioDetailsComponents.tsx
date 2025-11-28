import { css, styled, Typography, type TypographyProps } from "@mui/material";
import i18next from "i18next";
import React, { forwardRef } from "react";

export const PanelScenarioDetails = styled("div")(
    ({ theme }) => css`
        display: flex;
        flex-direction: column;
        padding: ${theme.spacing(1.5, 2)};
        gap: ${theme.spacing(1)};
    `,
);

export const PanelScenarioDetailsIcon = styled("div")(({ theme }) => ({
    display: "inline-block",
    width: "1rem",
    height: "1rem",
}));

export const ScenarioDetailsItemWrapper = styled("div")(
    ({ theme }) => css`
        display: flex;
        align-items: flex-start;
        gap: ${theme.spacing(1)};
    `,
);

const Typo = forwardRef(function Typo(props: TypographyProps, forwardedRef: React.Ref<HTMLElement>) {
    return <Typography ref={forwardedRef} title={i18next.t("panels.scenarioDetails.tooltip.name", "Name")} {...props} />;
});

export const ProcessName = styled(Typo)({
    overflow: "hidden",
    textOverflow: "ellipsis",
});

export const ProcessRename = styled(ProcessName)(({ theme }) => ({
    color: theme.palette.warning.main,
}));

export const ScenarioDetailsDescription = styled("div")`
    font-size: 12px;
    font-weight: lighter;
`;
