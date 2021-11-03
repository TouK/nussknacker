## req

-   node >=14
-   npm >=7
-   verify/update `.env` (or https://stackoverflow.com/questions/43069087/pass-shell-environment-variable-as-argument-in-npm-script)

## dev

1. `npm install` or `npm ci`
2. `npm start` or `NK_CORE_URL=http://... npm start`
3. read logs

## prod

1. `npm ci`
2. `npm run build` or `NK_CORE_URL=http://... npm run build`
3. wait for `dist(s)`
