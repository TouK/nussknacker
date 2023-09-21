import React from "react";
import { Box, CircularProgress, ThemeProvider, circularProgressClasses, createTheme } from "@mui/material";

type Props = {
    show: boolean;
};

function LoaderSpinner(props: Props) {
    return !props.show ? null : <CircularProgressWrapper />;
}

export default LoaderSpinner;

function CircularProgressWrapper() {
    const theme = createTheme({});
    return (
        <ThemeProvider theme={theme}>
            <Box sx={{ position: "absolute", left: "50%", top: "50% " }}>
                <CircularProgress
                    variant="determinate"
                    sx={{ color: (theme) => theme.palette.grey[200] }}
                    size={40}
                    thickness={4}
                    value={100}
                />
                <CircularProgress
                    variant="indeterminate"
                    disableShrink
                    sx={{
                        color: (theme) => theme.palette.grey[500],
                        animationDuration: "550ms",
                        position: "absolute",
                        left: 0,
                        [`& .${circularProgressClasses.circle}`]: {
                            strokeLinecap: "round",
                        },
                    }}
                    size={40}
                    thickness={4}
                />
            </Box>
        </ThemeProvider>
    );
}
