/*
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

package com.dtstack.flink.sql.sink.elasticsearch6;

import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.types.Row;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yinxi
 * @date 2020/1/9 - 15:10
 */
public class CustomerSinkFunc implements ElasticsearchSinkFunction<Tuple2> {

    private final Logger logger = LoggerFactory.getLogger(CustomerSinkFunc.class);

    private String index;

    private String type;

    private List<Integer> idFieldIndexList;

    private List<String> fieldNames;

    private List<String> fieldTypes;

    private transient Counter outRecords;

    private transient Counter outDirtyRecords;

    /** 默认分隔符为'_' */
    private char sp = '_';

    public CustomerSinkFunc(String index, String type, List<String> fieldNames, List<String> fieldTypes, List<Integer> idFieldIndexes){
        this.index = index;
        this.type = type;
        this.fieldNames = fieldNames;
        this.fieldTypes = fieldTypes;
        this.idFieldIndexList = idFieldIndexes;
    }

    @Override
    public void process(Tuple2 tuple2, RuntimeContext ctx, RequestIndexer indexer) {
        try {
            Tuple2<Boolean, Row> tupleTrans = tuple2;
            Boolean retract = tupleTrans.getField(0);
            Row element = tupleTrans.getField(1);
            if (!retract) {
                return;
            }

            indexer.add(createIndexRequest(element));
            outRecords.inc();
        } catch (Throwable e) {
            outDirtyRecords.inc();
            logger.error("Failed to store source data {}. ", tuple2.getField(1));
            logger.error("Failed to create index request exception. ", e);
        }
    }

    public void setOutRecords(Counter outRecords) {
        this.outRecords = outRecords;
    }

    public void setOutDirtyRecords(Counter outDirtyRecords) {
        this.outDirtyRecords = outDirtyRecords;
    }

    private IndexRequest createIndexRequest(Row element) {

        List<String> idFieldList = new ArrayList<>();
        for(int index : idFieldIndexList){
            if(index >= element.getArity()){
                continue;
            }

            idFieldList.add(element.getField(index).toString());
        }

        Map<String, Object> dataMap = Es6Util.rowToJsonMap(element,fieldNames,fieldTypes);
        int length = Math.min(element.getArity(), fieldNames.size());
        for(int i=0; i<length; i++){
            dataMap.put(fieldNames.get(i), element.getField(i));
        }

        if (idFieldList.size() == 0) {
            return Requests.indexRequest().index(index).type(type).source(dataMap);
        }

        String id = StringUtils.join(idFieldList, sp);
        return Requests.indexRequest()
                .index(index)
                .type(type)
                .id(id)
                .source(dataMap);
    }
}
