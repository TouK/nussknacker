export const delay = (time = 250) =>
    new Promise((resolve) => {
        setTimeout(resolve, time);
    });
