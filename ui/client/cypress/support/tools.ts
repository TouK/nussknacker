export const jsonToBlob = (data: any) => {
  return new Blob([JSON.stringify(data)], {type: "application/json"})
}
