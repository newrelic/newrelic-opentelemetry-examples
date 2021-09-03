module.exports = {
    mode: 'development',
    entry: {
        tracer: 'tracer.js',
    },
    output: {
        filename: '[name].js',
        sourceMapFilename: '[file].map',
    },
    target: 'web',
    module: {
        rules: [
        {
            test: /\.js$/,
            exclude: /(node_modules)/,
            use: { loader: 'babel-loader' },
        },
        ],
    },
    resolve: { modules: [__dirname, 'node_modules'], extensions: ['.js'] },
    devtool: 'eval-source-map',
};
