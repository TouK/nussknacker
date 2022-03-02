import { styled } from "@mui/material";
import React, { DetailedHTMLProps, ImgHTMLAttributes, useContext } from "react";
import { NkIconsContext } from "../../settings/nkApiProvider";

type Props = DetailedHTMLProps<ImgHTMLAttributes<HTMLImageElement>, HTMLImageElement>;

const StyledImg = styled("img")(({ theme }) => ({
    height: "1.5rem",
    verticalAlign: "middle",
    filter: theme.palette.mode === "light" ? "invert(1)" : null,
}));

export function IconImg({ src, ...props }: Props): JSX.Element {
    const { getComponentIconSrc } = useContext(NkIconsContext);
    return <StyledImg src={getComponentIconSrc(src)} {...props} />;
}
