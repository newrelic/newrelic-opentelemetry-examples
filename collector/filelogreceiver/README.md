In one shell window run:
```shell
docker build . -t logfilereceiver
docker run -t logfilereceiver
```

In another shell window run:
```shell
docker exec -it container_id bash
echo "Some log message" >> logfile.txt
```

You should see a log get exported via the logging exporter.