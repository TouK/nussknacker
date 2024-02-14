import { css, styled, Typography } from "@mui/material";

export const PanelProcessInfo = styled("div")(
    ({ theme }) => css`
        display: flex;
        flex-direction: column;
        padding: 15px 10px;
        gap: ${theme.spacing(1)};
    `,
);

export const PanelProcessInfoIcon = styled("div")`
    display: inline-block;
    width: 1rem;
    height: 1rem;
`;

export const ProcessInfoItemWrapper = styled("div")(
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

export const ProcessInfoDescription = styled("div")`
    font-size: 12px;
    font-weight: lighter;
`;
