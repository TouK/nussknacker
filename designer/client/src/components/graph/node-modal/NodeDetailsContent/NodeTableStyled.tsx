import { css, styled } from "@mui/material";
import { customCheckbox } from "./CustomCheckbox";

export const HIDDEN_TEXTAREA_PIXEL_HEIGHT = 100;
export const nodeInput = "node-input";
export const nodeValue = "node-value";
export const nodeInputWithError = "node-input-with-error";
export const rowAceEditor = "row-ace-editor";

export const NodeTableStyled = styled("div")(
    ({ theme }) => css`
        font-size: 11px;
        margin: 0 24px;
        .movable-row {
            margin-top: 0;
            flex-wrap: nowrap;
            column-gap: 5px;
            row-gap: 5px;
        }
        .node-block {
            &.removed {
                border: 1px solid ${theme.palette.error.main};
                padding: 5px;
            }
            &.added {
                border: 1px solid ${theme.palette.success.main};
                padding: 5px;
            }
        }
        .${nodeValue} {
            flex: 1;
            flex-basis: 60%;
            display: inline-block;
            width: 100%;
            textarea {
                overflow: hidden;
                height: auto;
            }
            ${customCheckbox("20px", theme)};
            input[type="checkbox"] {
                margin-top: 7px;
                margin-bottom: 7px;
            }
            &.partly-hidden {
                textarea {
                    height: ${HIDDEN_TEXTAREA_PIXEL_HEIGHT}px !important;
                }
            }
            &.${nodeInputWithError} {
                width: 100%;
                max-height: 35;
                &.switchable {
                    width: 70%;
                }
            }
            &.switchable {
                width: 70%;
            }
            .fadeout {
                position: absolute;
                bottom: 0;
                width: 100%;
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
        .${nodeInput} {
            height: 35px;
            width: 100%;
            padding: 0 10px;
            color: ${theme.palette.text.primary};
            font-weight: 400;
            font-size: 14px;
        }

        .${rowAceEditor} {
            padding: ${theme.spacing(1, 0.625)};
            min-height: 35px;
            .ace-nussknacker {
                outline: none;
            }
        }
        textarea.${nodeInput} {
            resize: vertical;
            line-height: 1.5;
            padding-top: 7px;
            padding-bottom: 7px;
        }
        input[type="checkbox"].${nodeInput} {
            height: 20px;
        }
        .${nodeInputWithError} {
            outline: 1px solid ${theme.palette.error.light} !important;
            outline-offset: initial !important;
            border-radius: 2px;
        }
        .marked {
            border: 2px solid ${theme.palette.success.main} !important;
        }
    `,
);
