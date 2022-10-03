export function getUserSetting(key: string): boolean {
    try {
        const settings = JSON.parse(
            localStorage.getItem("persist:settings"),
            (key, value) => value === "true" || (value === "false" ? false : value),
        );
        return settings[key];
    } catch {
        return null;
    }
}
