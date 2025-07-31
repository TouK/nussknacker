import React from "react";

import { isArchived } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/configureStore";
import type { ToolbarButtonProps } from "../../types";
import ArchiveButton from "../buttons/ArchiveButton";
import UnArchiveButton from "../buttons/UnArchiveButton";

export const ArchiveToggleButton = (props: ToolbarButtonProps) => {
    const archived = useAppSelector(isArchived);
    return archived ? <UnArchiveButton {...props} /> : <ArchiveButton {...props} />;
};
