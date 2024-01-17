import { css, styled } from "@mui/material";
import { customCheckbox } from "./CustomCheckbox";

export const NodeTableStyled = styled("div")(
    ({ theme }) => css`
        font-size: 11px;
        margin: 0 25px;
        .movable-row {
            margin-top: 0;
            flex-wrap: nowrap;
            column-gap: 5px;
            row-gap: 5px;
        }
        .node-block {
            &.removed {
                border: 1px solid ${theme.custom.colors.error};
                padding: 5px;
            }
            &.added {
                border: 1px solid ${theme.custom.colors.ok};
                padding: 5px;
            }
        }
        .node-value {
            flex: 1;
            flex-basis: 60%;
            display: inline-block;
            color: ${theme.custom.colors.dimGray};
            textarea {
                overflow: hidden;
                height: auto;
            }
            textarea:-moz-read-only {
                background-color: ${theme.custom.colors.tundora};
            }
            textarea:read-only {
                background-color: ${theme.custom.colors.tundora};
            }
            input:-moz-read-only {
                background-color: ${theme.custom.colors.tundora};
            }
            input:read-only {
                background-color: ${theme.custom.colors.tundora} !important;
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
            .fadeout {
                position: relative;
                bottom: 4em;
                height: 4em;
                background: -webkit-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
                background-image: -moz-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
                background-image: -o-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
                background-image: linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
                background-image: -ms-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
            }
        }
        .node-tip {
            width: 24px;
            height: 24px;
            svg {
                // disable svg <title> behavior
                pointer-events: none;
            }
        }
        .node-input {
            height: 35px;
            width: 100%;
            padding: 0 10px;
            border: none;
            background-color: ${theme.custom.colors.secondaryBackground};
            color: ${theme.custom.colors.secondaryColor};
            font-weight: 400;
            font-size: 14px;
            outline: 1px solid rgba(255, 255, 255, 0.075);
        }

        .row-ace-editor {
            color: ${theme.custom.colors.dimGray};
            padding-top: 8px;
            padding-bottom: 8px;
            padding-left: 5px;
            padding-right: 5px;
            background-color: ${theme.custom.colors.secondaryBackground};
            min-height: 35px;
            outline: 1px solid rgba(255, 255, 255, 0.075);
            &.focused {
                outline: 2px solid ${theme.custom.colors.cobalt};
                outline-offset: -1px;
            }
        }
        .node-input:-moz-disabled {
            background-color: ${theme.custom.colors.tundora};
        }
        .node-input:disabled {
            background-color: ${theme.custom.colors.tundora};
        }
        .read-only {
            background-color: ${theme.custom.colors.tundora};
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
            outline: 1px solid ${theme.custom.colors.error} !important;
            outline-offset: initial !important;
            border-radius: 2px;
        }
        .testResultDownload {
            padding-left: 15px;
            font-size: 14px;
            a {
                color: ${theme.custom.colors.canvasBackground};
                text-decoration: none;

                &:hover {
                    color: ${theme.custom.colors.info};
                }
            }
        }
        .marked {
            border: 2px solid ${theme.custom.colors.ok} !important;
        }
    `,
);
