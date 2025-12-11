export function heatmapColor(v: number, stops: { v: number; c: string }[]): string {
    const hexToRgb = (hex: string) => {
        const h = hex.replace("#", "");
        const n = parseInt(h, 16);
        return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
    };

    v = Math.min(1, Math.max(0, v));

    for (let i = 0; i < stops.length - 1; i++) {
        const a = stops[i];
        const b = stops[i + 1];
        if (v >= a.v && v <= b.v) {
            const [ar, ag, ab] = hexToRgb(a.c);
            const [br, bg, bb] = hexToRgb(b.c);
            const t = (v - a.v) / (b.v - a.v || 1);
            const red = Math.round(ar + (br - ar) * t);
            const green = Math.round(ag + (bg - ag) * t);
            const blue = Math.round(ab + (bb - ab) * t);
            return `rgb(${red},${green},${blue})`;
        }
    }

    return "#FFFFFF";
}
