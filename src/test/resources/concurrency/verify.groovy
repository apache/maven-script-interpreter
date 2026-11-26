/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

// prints, waits for the other script, prints on both streams, then stays alive until the bystander thread has
// printed too: output of a thread outside the scripts must not end up in either log
def seconds = java.util.concurrent.TimeUnit.SECONDS
println "start " + name
assert context.get("barrier").await(30, seconds) >= 0
System.out.println "system " + name
System.err.println "error " + name
context.get("printed").countDown()
assert context.get("bystanderDone").await(30, seconds)
println "end " + name
return true
