import { css, styled, Typography } from "@mui/material";

export const PanelScenarioDetails = styled("div")(
    ({ theme }) => css`
        display: flex;
        flex-direction: column;
        padding: ${theme.spacing(1.5, 2, 0.5)};
        gap: ${theme.spacing(1)};
    `,
);

export const PanelScenarioDetailsIcon = styled("div")(({ theme }) => ({
    display: "inline-block",
    width: "1rem",
    height: "1rem",
    marginTop: theme.spacing(0.5),
}));

export const ScenarioDetailsItemWrapper = styled("div")(
    ({ theme }) => css`
        display: flex;
        align-items: flex-start;
        gap: ${theme.spacing(1)};
    `,
);

export const ProcessName = styled(Typography)``;

export const ProcessRename = styled(ProcessName)(({ theme }) => ({
    color: theme.palette.warning.main,
}));

export const ScenarioDetailsDescription = styled("div")`
    font-size: 12px;
    font-weight: lighter;
`;
