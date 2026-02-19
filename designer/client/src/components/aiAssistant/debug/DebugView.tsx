import { Box } from "@mui/material";
import React, { useCallback, useEffect } from "react";

import { assistantOpen } from "../../../actions/assistantActions";
import { useAppDispatch } from "../../../store/storeHelpers";
import { delay } from "../../../utils";

function DebugView() {
    const dispatch = useAppDispatch();

    const open = useCallback(async () => {
        await delay(2000);
        dispatch(assistantOpen(true));
    }, [dispatch]);

    useEffect(() => {
        open();
    }, [open]);

    return <Box sx={{ padding: 2 }} />;
}

export default DebugView;
