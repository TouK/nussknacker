import { styled } from "@mui/material";
import { variables } from "../../stylesheets/variables";

export const VersionHeader = styled("div")`
    margin: 15px 30px;
`;

export const CompareModal = styled("div")`
    max-width: 1000px;
    font-size: 15px;
    font-weight: 700;
`;

export const CompareContainer = styled("div")`
    zoom: 0.9;
    > :first-child {
        width: 50%;
        display: inline-block;
        vertical-align: top;
    }

    > :nth-child(2) {
        width: 50%;
        display: inline-block;
        vertical-align: top;
    }
`;

export const FormRow = styled("div")(
    ({ theme }) => `
    margin: 7px 30px 8px 30px;
    > :first-child {
        width: 20%;
        color: ${variables.modalLabelTextColor};
        display: inline-block;
        vertical-align: top;
        padding-top: 10px;
        font-size: 12px;
        font-weight: 700;
    }

    > :nth-child(2) {
        width: 80%;
        height: 40px;
        padding: 0 20px;
        display: inline-block;
        color: ${theme.custom.colors.secondaryColor};
        background-color: ${variables.commentBkgColor};
        border: none;
        font-size: 14px;
        font-weight: 400;
    }
`,
);
