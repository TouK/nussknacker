import { Pagination, Stack, Typography } from "@mui/material";
import { gridPageCountSelector, gridRowCountSelector, useGridApiContext, useGridSelector } from "@mui/x-data-grid";
import React from "react";

export function CustomPagination({ allRows }: { allRows: number }): JSX.Element {
    const apiRef = useGridApiContext();
    const {
        pagination: {
            paginationModel: { page, pageSize },
        },
    } = apiRef.current.state;
    const pageCount = useGridSelector(apiRef, gridPageCountSelector);
    const rowCount = useGridSelector(apiRef, gridRowCountSelector);
    const firstOnPage = 1 + page * pageSize;
    const lastOnPage = Math.min(page * pageSize + pageSize, rowCount);

    return (
        <Stack direction="row" alignItems="center" spacing={2}>
            {pageCount > 0 && rowCount > 1 && (
                <div>
                    <Typography component="span" variant="body2" color="text.primary">
                        {firstOnPage}–{lastOnPage} of {rowCount}
                    </Typography>
                    {allRows > 0 && allRows !== rowCount && (
                        <Typography component="span" variant="body2" ml={1} color="text.secondary">
                            from {allRows}
                        </Typography>
                    )}
                </div>
            )}
            {pageCount > 1 ? (
                <Pagination count={pageCount} page={page + 1} onChange={(event, value) => apiRef.current.setPage(value - 1)} />
            ) : (
                <span />
            )}
        </Stack>
    );
}
