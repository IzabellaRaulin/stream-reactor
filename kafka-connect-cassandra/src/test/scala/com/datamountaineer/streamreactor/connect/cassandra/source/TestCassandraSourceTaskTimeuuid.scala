/*
 * Copyright 2017 Datamountaineer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.cassandra.source

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.LinkedBlockingQueue

import com.datamountaineer.streamreactor.connect.cassandra.TestConfig
import com.datamountaineer.streamreactor.connect.schemas.ConverterUtil
import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.connect.data.Schema
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{DoNotDiscover, Matchers, WordSpec}

import scala.collection.JavaConverters._
import com.datastax.driver.core.Session
import org.scalatest.BeforeAndAfterAll

@DoNotDiscover
class TestCassandraSourceTaskTimeuuid extends WordSpec 
    with Matchers
    with MockitoSugar
    with TestConfig
    with TestCassandraSourceUtil
    with ConverterUtil 
    with BeforeAndAfterAll {

  var session: Session = _
  val keyspace = "source"
  var tableName: String = _

  override def beforeAll {
    session = createKeySpace(keyspace, secure = true, ssl = false)
    tableName = createTimeuuidTable(session, keyspace)
  }

  override def afterAll(): Unit = {
    session.close()
    session.getCluster.close()
  }

  "A Cassandra SourceTask should read in incremental mode with timeuuid and time slices" in {
    val taskContext = getSourceTaskContextDefault
    val config = getCassandraConfigDefault
    val task = new CassandraSourceTask()
    task.initialize(taskContext)
    
    //start task
    task.start(config)

    insertIntoTimeuuidTable(session, keyspace, tableName, "id1", "magic_string")

    var records = pollAndWait(task, tableName)
    var sourceRecord = records.asScala.head
    //check a field
    var json: JsonNode = convertValueToJson(sourceRecord)
    println(json)
    json.get("string_field").asText().equals("magic_string") shouldBe true
    json.get("timeuuid_field").asText().size > 0
    json.get("int_field") shouldBe null
    
    
    insertIntoTimeuuidTable(session, keyspace, tableName, "id2", "magic_string2") 
    
    records = pollAndWait(task, tableName)
    sourceRecord = records.asScala.head
    //check a field
    json = convertValueToJson(sourceRecord)
    println(json)
    json.get("string_field").asText().equals("magic_string2") shouldBe true
    json.get("timeuuid_field").asText().size > 0
    json.get("int_field") shouldBe null
    
    //stop task
    task.stop()
  }
  
  "A Cassandra SourceTask should read in incremental mode with timeuuid and time slices and use ignore and unwrap" in {
    val taskContext = getSourceTaskContextDefault
    val config = getCassandraConfigWithUnwrap
    val task = new CassandraSourceTask()
    task.initialize(taskContext)
    
    //start task
    task.start(config)

    insertIntoTimeuuidTable(session, keyspace, tableName, "id1", "magic_string") 

    val records = pollAndWait(task, tableName)
    val sourceRecord = records.asScala.head
    sourceRecord.keySchema shouldBe null
    sourceRecord.key shouldBe null
    sourceRecord.valueSchema shouldBe Schema.STRING_SCHEMA
    sourceRecord.value shouldBe "magic_string"

    //stop task
    task.stop()
  }  
  
  "A Cassandra SourceTask should throw exception when timeuuid column is not specified" in {
    val taskContext = getSourceTaskContextDefault
    val config = getCassandraConfigWithKcqlNoPrimaryKeyInSelect
    val task = new CassandraSourceTask()
    task.initialize(taskContext)
    
    insertIntoTimeuuidTable(session, keyspace, tableName, "id1", "magic_string") 
    
    try {
      task.start(config)
      fail()
    } catch {
      case _: ConfigException => // Expected, so continue
    }
    task.stop()
  }

  private def getCassandraConfigWithKcqlNoPrimaryKeyInSelect() = {
    val myKcql = s"INSERT INTO sink_test SELECT string_field FROM $tableName PK timeuuid_field INCREMENTALMODE=timeuuid"
    getCassandraConfig(keyspace, tableName, myKcql)
  }

  private def getCassandraConfigWithUnwrap() = {
    val myKcql = s"INSERT INTO sink_test SELECT string_field, timeuuid_field FROM $tableName IGNORE timeuuid_field PK timeuuid_field WITHUNWRAP INCREMENTALMODE=timeuuid"
    getCassandraConfig(keyspace, tableName, myKcql)
  }
  
  private def getCassandraConfigDefault() = {
    val myKcql = s"INSERT INTO sink_test SELECT string_field, timeuuid_field FROM $tableName PK timeuuid_field INCREMENTALMODE=timeuuid"
    getCassandraConfig(keyspace, tableName, myKcql)
  }
  
}

