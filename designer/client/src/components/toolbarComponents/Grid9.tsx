import { Box, styled } from "@mui/material";

export const Grid9 = styled(Box)({
    display: "grid",
    gridTemplateColumns: "[left] min-content [center] 1fr [right] min-content",
    gridTemplateRows: "[top] min-content [center] 1fr [bottom] min-content",
    gridTemplateAreas: ` 
        "left top right"
        "left body right"
        "left bottom right"
    `,
});
