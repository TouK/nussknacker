import { useTheme } from "@mui/material";
import React, { useContext } from "react";
import { NkIconsContext } from "../../settings/nkApiProvider";

export function IconImg({ title, src }: { title?: string; src: string }): JSX.Element {
    const { palette } = useTheme();
    const { getComponentIconSrc } = useContext(NkIconsContext);

    return (
        <img
            title={title}
            style={{
                height: "1.5rem",
                verticalAlign: "middle",
                filter: palette.mode === "light" ? "invert()" : null,
            }}
            src={getComponentIconSrc(src)}
        />
    );
}
