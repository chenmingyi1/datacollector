/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.lib.stage.source.spooldir.log;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.lib.io.OverrunLineReader;
import com.streamsets.pipeline.lib.io.PositionableReader;
import com.streamsets.pipeline.lib.stage.source.spooldir.AbstractSpoolDirSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@StageDef(name = "logSpoolDirectory",
    version = "1.0.0",
    label = "Log spool directory",
    description = "Consumes log files from a spool directory")
public class LogSpoolDirSource extends AbstractSpoolDirSource {
  private final static Logger LOG = LoggerFactory.getLogger(LogSpoolDirSource.class);

  @ConfigDef(name="logLineFieldName",
      required = true,
      type = ConfigDef.Type.STRING,
      label = "Log Line Field Name",
      description = "The name of the record field to hold the log line",
      defaultValue = "line")
  public String logLineFieldName;

  @ConfigDef(name="maxLogLineLength",
      required = true,
      type = ConfigDef.Type.INTEGER,
      label = "Maximum Log Line Length",
      description = "The maximum length for log lines, if a line exceeds that length, it will be trimmed",
      defaultValue = "1024")
  public int maxLogLineLength;

  @Override
  protected long produce(File file, long offset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    String sourceFile = file.getName();
    try (OverrunLineReader lineReader =
             new OverrunLineReader(new PositionableReader(new FileReader(file), offset), 8192, maxLogLineLength)) {
      return produce(sourceFile, offset, lineReader, maxBatchSize, batchMaker);
    } catch (IOException ex) {
      throw new StageException(null, ex.getMessage(), ex);
    }
  }

  protected long produce(String sourceFile, long offset, OverrunLineReader lineReader, int maxBatchSize,
      BatchMaker batchMaker) throws IOException {
    StringBuilder sb = new StringBuilder(maxLogLineLength);
    for (int i = 0; i < maxBatchSize; i++) {
      sb.setLength(0);
      int len = lineReader.readLine(sb);
      if (len > maxLogLineLength) {
        LOG.warn("Log line exceeds maximum length '{}', log file '{}', line starts at offset '{}'", maxLogLineLength,
                 sourceFile, offset);
      }

      Record record = getContext().createRecord(sourceFile + "::" + offset);
      record.setField(logLineFieldName, Field.create(sb.toString()));
      batchMaker.addRecord(record);

      offset = lineReader.getCount();
    }
    return offset;
  }

}
