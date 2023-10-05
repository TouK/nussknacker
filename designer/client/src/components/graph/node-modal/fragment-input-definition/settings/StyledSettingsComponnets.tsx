import { styled } from "@mui/material";
import { variables } from "../../../../../stylesheets/variables";
import { NodeRow } from "../../../node-modal/NodeDetailsContent/NodeStyled";

export const SettingsWrapper = styled("div")`
    padding: 10px;
    border: 1px solid #ffffff1f;
    width: 100%;
    display: flex;
    flex-direction: column;
    margin-bottom: 20px;
`;

export const SettingLabelStyled = styled("div")`
    font-family: Open Sans;
    color: ${variables.modalLabelTextColor};
    font-size: 12px;
    font-weight: 400;
    line-height: 16px;
    letter-spacing: -0.01em;
    text-align: left;
`;

export const SettingRow = styled(NodeRow)`
    align-items: center;
`;
