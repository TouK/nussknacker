import { styled } from "@mui/material";

export const VersionHeader = styled("div")`
    margin: 15px 30px;
`;

export const CompareModal = styled("div")`
    font-size: 15px;
    font-weight: 700;
`;

export const CompareContainer = styled("div")`
    zoom: 0.9;
    > div:nth-of-type(1),
    > div:nth-of-type(2) {
        width: 50%;
        display: inline-block;
        vertical-align: top;
    }
`;

export const FormRow = styled("div")(
    ({ theme }) => `
    margin: 7px 30px 8px 30px;
    > p {
        width: 20%;
        color: ${theme.custom.colors.canvasBackground};
        display: inline-block;
        vertical-align: top;
        padding-top: 10px;
        font-size: 12px;
        font-weight: 700;
    }

    > select {
        width: 80%;
        height: 40px;
        padding: 0 20px;
        display: inline-block;
        color: ${theme.custom.colors.secondaryColor};
        background-color: ${theme.custom.colors.secondaryColor};
        border: none;
        font-size: 14px;
        font-weight: 400;
    }
`,
);
