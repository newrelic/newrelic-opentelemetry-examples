package com.newrelic.otlp;

import com.google.protobuf.MessageOrBuilder;
import java.util.List;

interface TestCaseProvider<T extends MessageOrBuilder> {

  void exportGrpcProtobuf(T request);

  List<TestCase<T>> testCases();

  String newRelicDataType();
}
