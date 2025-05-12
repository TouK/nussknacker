import React from "react";
import { useSelector } from "react-redux";

import { isArchived } from "../../../../reducers/selectors/graph";
import type { ToolbarButtonProps } from "../../types";
import ArchiveButton from "../buttons/ArchiveButton";
import UnArchiveButton from "../buttons/UnArchiveButton";

export const ArchiveToggleButton = (props: ToolbarButtonProps) => {
    const archived = useSelector(isArchived);
    return archived ? <UnArchiveButton {...props} /> : <ArchiveButton {...props} />;
};
