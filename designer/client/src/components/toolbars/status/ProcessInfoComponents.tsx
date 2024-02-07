import { styled, Typography } from "@mui/material";

export const PanelProcessInfo = styled("div")`
    display: flex;
    padding: 15px 10px;
`;

export const PanelProcessInfoIcon = styled("div")`
    display: inline-block;
    width: 32px;
    height: 32px;
`;

export const ProcessInfoText = styled("div")`
    display: inline-block;
    margin-left: 7px;
    font-size: 14px;
    font-weight: bold;
    color: #b3b3b3;
    vertical-align: middle;
`;

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
