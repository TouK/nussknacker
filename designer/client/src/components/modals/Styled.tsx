import { styled, Typography } from "@mui/material";

export const VersionHeader = styled(
    Typography,
    {},
)(({ theme }) => ({
    margin: theme.spacing(2, 4),
}));

VersionHeader.defaultProps = {
    variant: "subtitle1",
};
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
