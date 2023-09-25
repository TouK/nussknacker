import { styled } from "@mui/material";
import { variables } from "../../../../stylesheets/variables";
import { customCheckbox } from "./CustomCheckbox";

export const NodeTableStyled = styled("div")`
    font-size: 11px;
    margin: 0 25px;
    .node-table-body {
        clear: both;
    }
    .node-row {
        margin-bottom: 14px;
        margin-top: 14px;
        display: flex;
        flex-wrap: wrap;
    }
    .movable-row {
        margin-top: 0;
        flex-wrap: nowrap;
        column-gap: 5px;
        row-gap: 5px;
    }
    .node-label {
        color: ${variables.modalLabelTextColor};
        flex-basis: 20%;
        max-width: 20em;
        display: inline-block;
        vertical-align: sub;
        margin-top: 9px;
        font-size: 12px;
        font-weight: 700;
        span {
            margin-top: 10px;
            margin-left: 10px;
            font-size: 15px;

            &:hover {
                cursor: pointer;
            }
        }
    }
    .node-block {
        &.removed {
            border: 1px solid ${variables.errorColor};
            padding: 5px;
        }
        &.added {
            border: 1px solid ${variables.okColor};
            padding: 5px;
        }
    }
    .node-value {
        flex: 1;
        flex-basis: 60%;
        display: inline-block;
        color: #686868;
        textarea {
            overflow: hidden;
            height: auto;
        }
        textarea:-moz-read-only {
            background-color: ${variables.panelBkgColor};
        }
        textarea:read-only {
            background-color: ${variables.panelBkgColor};
        }
        input:-moz-read-only {
            background-color: ${variables.panelBkgColor};
        }
        input:read-only {
            background-color: ${variables.panelBkgColor};
        }
        ${customCheckbox("20px")};
        input[type="checkbox"] {
            margin-top: 7px;
            margin-bottom: 7px;
        }
        &.partly-hidden {
            textarea {
                height: 100px !important;
            }
        }
        &.node-value-type-select {
            width: 100%;
            max-height: 35;
            outline: 1px solid rgba(255, 255, 255, 0.075);
            &.switchable {
                width: 70%;
            }
        }
        &.switchable {
            width: 70%;
        }
    }
    .node-error {
        width: 100%;
        color: ${variables.errorColor};
        font-size: 14px;
        font-weight: 400;
        margin-bottom: 10px;
        margin-top: 10px;
    }
    .node-tip {
        margin-left: 10px;
        width: 15px;
        height: 15px;
        svg {
            // disable svg <title> behavior
            pointer-events: none;
        }
        &.node-error-tip {
            margin-right: 10px;
            float: right;
        }
    }
    .node-test-results {
        border: 1px solid ${variables.okColor};
        padding: 5px;
    }
    .node-input {
        height: 35px;
        width: 100%;
        padding: 0 10px;
        border: none;
        background-color: ${variables.commentBkgColor};
        color: ${variables.defaultTextColor};
        font-weight: 400;
        font-size: 14px;
        outline: 1px solid rgba(255, 255, 255, 0.075);
    }
    .node-input:-moz-disabled {
        background-color: ${variables.panelBkgColor};
    }
    .node-input:disabled {
        background-color: ${variables.panelBkgColor};
    }
    .read-only {
        background-color: ${variables.panelBkgColor};
        span {
            margin-top: 10px;
            font-size: 15px;
            &:hover {
                cursor: pointer;
            }
        }
    }
    .node-group {
        padding-top: 15px;
        width: 100%;
        padding-left: 50px;
        .node-label {
            text-transform: none;
        }
    }
    textarea.node-input {
        resize: vertical;
        line-height: 1.5;
        padding-top: 7px;
        padding-bottom: 7px;
    }
    input[type="checkbox"].node-input {
        height: 20px;
    }
    .node-input-with-error {
        outline: 1px solid ${variables.errorColor} !important;
        border-radius: 2px;
    }
    .testResultDownload {
        padding-left: 15px;
        font-size: 14;
        a {
            color: ${variables.modalLabelTextColor};
            text-decoration: none;
            &:hover {
                color: ${variables.infoColor};
            }
        }
    }
`;
