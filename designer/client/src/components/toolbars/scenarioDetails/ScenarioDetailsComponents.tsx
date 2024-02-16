import { css, styled, Typography } from "@mui/material";

export const PanelScenarioDetails = styled("div")(
    ({ theme }) => css`
        display: flex;
        flex-direction: column;
        padding: ${theme.spacing(1.5, 1, 0.5)};
        gap: ${theme.spacing(1)};
    `,
);

export const PanelScenarioDetailsIcon = styled("div")`
    display: inline-block;
    width: 1rem;
    height: 1rem;
`;

export const ScenarioDetailsItemWrapper = styled("div")(
    ({ theme }) => css`
        display: flex;
        align-items: center;
        gap: ${theme.spacing(1)};
    `,
);

export const ProcessName = styled(Typography)`
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 215px;
    white-space: pre;
`;

export const ProcessRename = styled(ProcessName)`
    color: orange;
`;

export const ScenarioDetailsDescription = styled("div")`
    font-size: 12px;
    font-weight: lighter;
`;
