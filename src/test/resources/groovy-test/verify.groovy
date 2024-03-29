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

if ( !( basedir instanceof File ) )
{
    println "Global script variable not defined: basedir or not a File"
    throw new RuntimeException("Global script variable not defined: basedir or not a File");
}
def verify = new File( basedir, "verify.groovy" )
assert (verify.exists())

if ( !( context instanceof Map ) )
{
    println "Global script variable not defined: context or not a Map"
    throw new RuntimeException("Global script variable not defined: context or not a Map");
}

System.out.println("foo="+context.get("foo"));

if (binding.variables.containsKey("globalVar")) System.out.println("globalVar="+globalVar);

return true
