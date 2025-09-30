export function convertViewportUnitToPixels(unitString: string): number {
    const trimmed = unitString.trim();
    const value = parseFloat(trimmed);

    if (isNaN(value)) {
        throw new Error("Invalid input");
    }

    if (trimmed.endsWith("vw")) {
        return (value / 100) * window.innerWidth;
    } else if (trimmed.endsWith("vh")) {
        return (value / 100) * window.innerHeight;
    } else {
        throw new Error("Unsupported unit. Use 'vw' or 'vh'.");
    }
}
