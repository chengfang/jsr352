/*
 * Copyright (c) 2014 Red Hat, Inc. and/or its affiliates.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cheng Fang - Initial API and implementation
 */

package org.jberet.support.io;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;

import org.jberet.runtime.JobExecutionImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * A test class that reads xml resource into java object and write out to xml format.
 */
public final class XmlItemReaderTest {
    static final String jobName = "org.jberet.support.io.XmlItemReaderTest";
    private final JobOperator jobOperator = BatchRuntime.getJobOperator();
    static final String movieXml = "http://mysafeinfo.com/api/data?list=topmoviesboxoffice2012&format=xml";

    @Test
    public void testXmlMovieBeanType1_2() throws Exception {
        testReadWrite0(movieXml, "testXmlMovieBeanType1_2.out", "1", "2", Movie.class, MovieTest.expect1_2, MovieTest.forbid1_2);
    }

    @Test
    public void testXmlMovieBeanType2_4() throws Exception {
        testReadWrite0(movieXml, "testXmlMovieBeanType2_4.out", "2", "4", Movie.class, MovieTest.expect2_4, MovieTest.forbid2_4);
    }

    @Test
    public void testXmlMovieBeanTypeFull() throws Exception {
        testReadWrite0(movieXml, "testXmlMovieBeanTypeFull.out", null, null, Movie.class, null, null);
    }

    @Test
    public void testXmlMovieBeanTypeFull1_100() throws Exception {
        testReadWrite0(movieXml, "testXmlMovieBeanTypeFull1_100.out", "1", "100", Movie.class, MovieTest.expectFull, null);
    }

    private void testReadWrite0(final String resource, final String writeResource,
                                final String start, final String end, final Class<?> beanType,
                                final String expect, final String forbid) throws Exception {
        final Properties params = CsvItemReaderWriterTest.createParams(CsvProperties.BEAN_TYPE_KEY, beanType.getName());
        params.setProperty(CsvProperties.RESOURCE_KEY, resource);

        final File file = new File(CsvItemReaderWriterTest.tmpdir, writeResource);
        params.setProperty("writeResource", file.getPath());

        if (start != null) {
            params.setProperty(CsvProperties.START_KEY, start);
        }
        if (end != null) {
            params.setProperty(CsvProperties.END_KEY, end);
        }
        CsvItemReaderWriterTest.setRandomWriteMode(params);

        final long jobExecutionId = jobOperator.start(jobName, params);
        final JobExecutionImpl jobExecution = (JobExecutionImpl) jobOperator.getJobExecution(jobExecutionId);
        jobExecution.awaitTermination(CsvItemReaderWriterTest.waitTimeoutMinutes, TimeUnit.MINUTES);
        Assert.assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());

        CsvItemReaderWriterTest.validate(file, expect, forbid);
    }
}
