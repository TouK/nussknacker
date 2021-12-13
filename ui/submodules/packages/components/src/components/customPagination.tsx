import { Pagination, Stack, Typography } from "@mui/material";
import { useGridApiContext, useGridState } from "@mui/x-data-grid";
import React from "react";

export function CustomPagination({ allRows }: { allRows: number }): JSX.Element {
    const apiRef = useGridApiContext();
    const [
        {
            pagination: { page, pageCount, pageSize, rowCount },
        },
    ] = useGridState(apiRef);

    const firstOnPage = 1 + page * pageSize;
    const lastOnPage = Math.min(page * pageSize + pageSize, rowCount);
    return (
        <Stack direction="row" alignItems="center" spacing={4}>
            {pageCount > 0 && rowCount > 1 && (
                <Typography component="div" variant="body2" color="text.primary">
                    {firstOnPage}–{lastOnPage} of {rowCount}
                    {allRows > 0 && allRows !== rowCount && (
                        <Typography component="span" variant="body2" ml={1} color="text.secondary">
                            from {allRows}
                        </Typography>
                    )}
                </Typography>
            )}
            <Pagination count={pageCount} page={page + 1} onChange={(event, value) => apiRef.current.setPage(value - 1)} />
        </Stack>
    );
}
