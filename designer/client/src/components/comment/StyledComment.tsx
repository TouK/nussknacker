import { darken, lighten, styled } from "@mui/material";
import { NkButton } from "../button/NkButton";
import { StyledCloseIcon } from "../toolbarComponents/toolbarWrapper/ToolbarStyled";
import { css } from "@emotion/css";

export const ProcessCommentsWrapper = styled("div")`
    padding: 0 13px 10px;
`;

export const ProcessCommentsList = styled("div")`
    font-size: 10px;
    margin: 15px 0;
    padding: 0;
`;

export const RemoveButton = styled(StyledCloseIcon)`
    float: right;
    &:hover {
        cursor: pointer;
        opacity: 0.5;
    }
`;

export const AddCommentPanel = styled("div")(
    ({ theme }) => `
    font-size: 12px !important;
    display: flex;
    flex-direction: column !important;
    textarea {
        width: 100% !important;
        height: ${theme.custom.spacing.controlHeight} !important;
        font-size: 12px;
        font-weight: 400;
        border-radius: 3px;
        border: none;
        background-color: ${lighten(theme.palette.background.paper, 0.1)};
        padding: 4px 6px;
        resize: none;
        &:focus {
            outline-color: ${theme.custom.colors.secondaryColor};
        }
    }
`,
);

export const CommentButton = styled(NkButton)(
    ({ theme }) => `
    font-size: 12px !important;
    background-color: ${lighten(theme.palette.background.paper, 0.2)} !important;
    border: none !important;
    width: 20% !important;
    height: 30px !important;
    align-self: flex-end !important;
    margin: 5px 0 10px !important;
    padding: 3px 0 !important;
    border-radius: 3px !important;
    cursor: pointer;
    &:hover {
        background-color: ${lighten(theme.palette.background.paper, 0.2)} !important;
    }
`,
);

export const PanelComment = styled("div")(
    ({ theme }) => `
    margin-top: 1px;
    font-size: 12px;
    word-break: break-word;
    a {
        color: ${theme.custom.colors.pictonBlue} !important;
    }
    p {
        width: 90%;
        margin-left: 0;
        margin-right: 0;
    }
  `,
);
