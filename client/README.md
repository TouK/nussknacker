# Prerequisites
To run make sure that you have the newest version of nodejs (currently it is 6.3.0) and npm (currently it is 3.10.3).
If you have older versions follow the insruction on http://askubuntu.com/questions/594656/how-to-install-the-latest-versions-of-nodejs-and-npm-for-ubuntu-14-04-lts
After that you may need to cleanup npm global modules (result of `sudo npm -g list` should be blank).

# Run
```
npm install
npm start
open http://localhost:3000
```

# Background
As a initial template was used todomvc example from https://github.com/gaearon/redux-devtools (commit 619a18b26a5482585b10eddd331ccacf582ba913)
It contains:
- react in version 15.0.1
- redux in version 3.1.1
- redux state monitoring toolbox (ctrl-H to hide) 
- react-hot-loader in version 3.0.0-beta.1
