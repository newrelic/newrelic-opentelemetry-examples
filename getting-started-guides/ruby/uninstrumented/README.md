# Uninstrumented Ruby demo app

Requires:

* Ruby 3.2.2
* Bundler
* A New Relic account

To run the uninstrumented Ruby app via the CLI, switch to the `getting-started-guides/ruby/uninstrumented` directory and run:
```shell
bundle install
```

To start the server, run:
```shell
bundle exec rackup
```

To exercise, in a new shell:
```shell
./load-generator.sh
```

To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`.
