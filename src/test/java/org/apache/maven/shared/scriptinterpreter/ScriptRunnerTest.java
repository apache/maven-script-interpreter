package org.apache.maven.shared.scriptinterpreter;

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

import org.apache.maven.shared.utils.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Olivier Lamy
 */
public class ScriptRunnerTest
{

    @Test
    public void testBeanshell() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable( "globalVar", "Yeah baby it's rocks" );
            scriptRunner.run( "test", new File( "src/test/resources/bsh-test" ), "verify",
                    buildContext(), fileLogger );
        }

        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/bsh-test/verify.bsh" ).getPath() ) );
        assertTrue( logContent.contains( "foo=bar" ) );
        assertTrue( logContent.contains( "globalVar=Yeah baby it's rocks" ) );

        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void failedBeanshellShouldCreateProperLogsMessage() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        Exception catchedException = null;

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run( "test", new File( "src/test/resources/bsh-test" ), "failed",
                    buildContext(), fileLogger );
        }
        catch ( Exception e )
        {
            catchedException = e;
        }

        assertTrue( catchedException instanceof ScriptEvaluationException );
        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/bsh-test/failed.bsh" ).getPath() ) );
        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void noReturnFromBeanshellShouldThrowException() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        Exception catchedException = null;

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run( "test", new File( "src/test/resources/bsh-test" ),
                    "no-return", buildContext(), fileLogger );
        }
        catch ( Exception e )
        {
            catchedException = e;
        }

        assertTrue( catchedException instanceof ScriptEvaluationException );
        assertEquals( "The test returned null.", catchedException.getMessage() );
        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/bsh-test/no-return.bsh" ).getPath() ) );
        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void testBeanshellWithFile() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable( "globalVar", "Yeah baby it's rocks" );
            scriptRunner.run( "test", new File( "src/test/resources/bsh-test/verify.bsh" ),
                    buildContext(), fileLogger );
        }

        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/bsh-test/verify.bsh" ).getPath() ) );
        assertTrue( logContent.contains( "foo=bar" ) );

        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void testGroovy() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.setGlobalVariable( "globalVar", "Yeah baby it's rocks" );
            scriptRunner.run( "test", new File( "src/test/resources/groovy-test" ), "verify",
                    buildContext(), fileLogger );
        }

        String logContent = FileUtils.fileRead( logFile );
        assertTrue(
                logContent.contains( new File( "src/test/resources/groovy-test/verify.groovy" ).getPath() ) );
        assertTrue( logContent.contains( "foo=bar" ) );
        assertTrue( logContent.contains( "globalVar=Yeah baby it's rocks" ) );

        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void failedGroovyShouldCreateProperLogsMessage() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        Exception catchedException = null;

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run( "test", new File( "src/test/resources/groovy-test" ), "failed",
                    buildContext(), fileLogger );
        }
        catch ( Exception e )
        {
            catchedException = e;
        }

        assertTrue( catchedException instanceof ScriptEvaluationException );
        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/groovy-test/failed.groovy" ).getPath() ) );
        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void groovyReturnedFalseShouldThrowException() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        Exception catchedException = null;

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run( "test", new File( "src/test/resources/groovy-test" ),
                    "return-false", buildContext(), fileLogger );
        }
        catch ( Exception e )
        {
            catchedException = e;
        }

        assertTrue( catchedException instanceof ScriptEvaluationException );
        assertEquals( "The test returned false.", catchedException.getMessage() );
        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/groovy-test/return-false.groovy" ).getPath() ) );
        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    @Test
    public void testGroovyWithFile() throws Exception
    {
        File logFile = new File( "target/build.log" );
        if ( logFile.exists() )
        {
            logFile.delete();
        }

        TestMirrorHandler mirrorHandler = new TestMirrorHandler();

        try ( FileLogger fileLogger = new FileLogger( logFile, mirrorHandler ) )
        {
            ScriptRunner scriptRunner = new ScriptRunner();
            scriptRunner.run( "test", new File( "src/test/resources/groovy-test/verify.groovy" ),
                    buildContext(), fileLogger );
        }

        String logContent = FileUtils.fileRead( logFile );
        assertTrue( logContent.contains( new File( "src/test/resources/groovy-test/verify.groovy" ).getPath() ) );
        assertTrue( logContent.contains( "foo=bar" ) );

        assertEquals( logContent, mirrorHandler.getLoggedMessage() );
    }

    private Map<String, ?> buildContext()
    {
        Map<String, Object> context = new HashMap<>();
        context.put( "foo", "bar" );
        return context;
    }
}
