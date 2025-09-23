import { AccountTree } from "@mui/icons-material";
import React from "react";

import { NuIcon } from "../../common/nuIcon";
import { TableCellAvatar } from "./tableCellAvatar";

export function ComponentAvatar({ src, title, fragment }: { src: string; title?: string; fragment?: boolean }) {
    return <TableCellAvatar>{fragment ? <AccountTree titleAccess={title} /> : <NuIcon title={title} src={src} />}</TableCellAvatar>;
}
