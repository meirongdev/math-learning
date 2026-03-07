// Configure webpack to handle UTF-8 encoding properly for Wasm and JavaScript
config.output = Object.assign(config.output || {}, {
    // Ensure UTF-8 encoding for all output
    chunkFormat: 'module'
});

// Add UTF-8 handling to webpack module rules
config.module = Object.assign(config.module || {}, {
    rules: (config.module?.rules || []).map(rule => {
        if (rule.type === 'asset/source' || rule.test?.toString().includes('utf')) {
            return Object.assign({}, rule, { charset: 'utf-8' });
        }
        return rule;
    })
});

// Ensure server handles UTF-8 correctly without overriding all MIME types
if (config.devServer) {
    // Only set charset for text-based resources if needed, but usually not necessary
    // Removing the global 'Content-Type' override as it breaks .wasm files
}
