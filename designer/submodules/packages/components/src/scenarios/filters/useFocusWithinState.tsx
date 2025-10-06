import { useState } from "react";
import { useFocusWithin } from "rooks";

export function useFocusWithinState() {
    const [focused, onFocusWithinChange] = useState(false);
    const { focusWithinProps } = useFocusWithin({ onFocusWithinChange });
    return { focused, focusWithinProps };
}
