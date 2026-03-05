// Configure webpack dev server to use port 8081 to avoid conflict with backend (port 8080)
config.devServer = Object.assign(config.devServer || {}, {
    port: 8081
});
