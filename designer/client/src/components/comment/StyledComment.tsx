import { styled } from "@mui/material";
import { NkButton } from "../NkButton";
import { variables } from "../../stylesheets/variables";

export const ProcessCommentsWrapper = styled("div")`
    padding: 0 13px 10px;
`;

export const ProcessCommentsList = styled("div")`
    font-size: 10px;
    margin: 15px 0;
    padding: 0;
    p {
        font-size: 12px;
    }
    .header {
        width: 100%;
        span {
            color: ${variables.commentHeaderColor};
            &.date {
                color: ${variables.commentHeaderColor};
                font-style: italic;
            }
        }
        p {
            font-style: italic;
            color: ${variables.panelTitleTextColor};
        }
        .footer {
            font-style: italic;
            p {
                margin-bottom: 0;
            }
        }
        .remove {
            float: right;
            &:hover {
                cursor: pointer;
                opacity: 0.5;
            }
        }
    }
`;

export const AddCommentPanel = styled("div")`
    font-size: 12px !important;
    display: flex;
    flex-direction: column !important;
    textarea {
        width: 100% !important;
        height: ${variables.formControllHeight}px !important;
        font-size: 12px;
        font-weight: 400;
        border-radius: 3px;
        border: none;
        background-color: ${variables.commentBkgColor};
        padding: 4px 6px;
        resize: none;
        &:focus {
            outline-color: ${variables.defaultTextColor};
        }
    }
`;

export const CommentNKButton = styled(NkButton)`
    font-size: 12px !important;
    background-color: ${variables.commentBkgColor} !important;
    border: none !important;
    width: 20% !important;
    align-self: flex-end !important;
    margin-top: 5px !important;
    padding: 3px 0 !important;
    border-radius: 3px !important;
    cursor: pointer;
    &:hover {
        background-color: #3d3d3d !important;
    }
`;

export const PanelComment = styled("div")`
    margin-top: 1px;
    font-size: 12px;
    word-break: break-word;
    p {
        width: 90%;
        margin-left: 0;
        margin-right: 0;
    }
`;
