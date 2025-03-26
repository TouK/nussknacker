export function jsonToFileInFormData(jsonData: any, fileName = "data.json"): File {
    const jsonString = JSON.stringify(jsonData);
    const blob = new Blob([jsonString], { type: "application/json" });
    return new File([blob], fileName, { type: "application/json" });
}
