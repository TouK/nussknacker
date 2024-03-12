import { darken, lighten, styled } from "@mui/material";
import { NkButton } from "../button/NkButton";

export const AddAttachmentsWrapper = styled("div")`
    font-size: 12px;
    margin-bottom: 15px;
    height: 90px;
    border: none;
`;

export const AttachmentsContainer = styled("div")`
    height: 100%;
`;

export const AttachmentDropZone = styled("div")(({ theme }) => ({
    width: "100%",
    height: "100%",
    padding: 0,
    cursor: "pointer",
    paddingTop: theme.spacing(2),
    marginBottom: "5px",
    textAlign: "center",
    transition: "background-color 0.2s",
    backgroundColor: lighten(theme.palette.background.paper, 0.1),
    "&:hover": {
        backgroundColor: theme.palette.action.hover,
    },
    svg: {
        width: "40px",
        margin: "auto",
        display: "inline-block",
    },
}));

export const AttachmentButton = styled("div")`
    border-radius: 3px;

    svg {
        margin-top: 10px;
        margin-bottom: 3px;
        width: 22px;
        height: 22px;
    }
`;

export const AttachmentDetails = styled("div")`
    margin-left: 8px;
    word-break: break-word;
`;

export const DownloadAttachment = styled("div")`
    margin-right: 5px;
    cursor: pointer;
    display: inline-block;
    font-size: 25px;
`;

export const DownloadButton = styled(NkButton)(
    ({ theme }) => `
    width: 27px !important;
    height: 27px !important;
    border: 1px solid ${theme.custom.colors.tundora};
`,
);

export const AttachHeader = styled("div")(
    ({ theme }) => `
    span {
        color: ${theme.custom.colors.silverChalice};
        &.date {
            color: ${theme.custom.colors.silverChalice};
            font-style: italic;
        }
    }
    p {
        font-style: italic;
        color: ${theme.custom.colors.mutedColor};
    }
`,
);

export const ProcessAttachmentsStyled = styled("div")`
    cursor: default;
    padding: 0 13px;
    display: grid;
`;

export const ProcessAttachmentsList = styled("div")`
    font-size: 10px;
    margin: 15px 0;
    padding: 0;
    .footer {
        font-style: italic;
        p {
            margin-bottom: 0;
        }
    }
    p {
        font-size: 12px;
    }
`;
