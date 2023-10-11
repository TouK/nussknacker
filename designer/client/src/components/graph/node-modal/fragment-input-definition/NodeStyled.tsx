import { styled } from "@mui/material";

export const NodeLabelStyled = styled("div")(
    ({ theme }) => `
    color: ${theme.custom.colors.canvasBackground};
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
`,
);
