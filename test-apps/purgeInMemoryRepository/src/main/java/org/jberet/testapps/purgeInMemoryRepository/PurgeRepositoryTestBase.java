/*
 * Copyright (c) 2015 Red Hat, Inc. and/or its affiliates.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cheng Fang - Initial API and implementation
 */

package org.jberet.testapps.purgeInMemoryRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.batch.operations.NoSuchJobException;
import javax.batch.operations.NoSuchJobInstanceException;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;

import org.jberet.repository.JobExecutionSelector;
import org.jberet.runtime.JobExecutionImpl;
import org.jberet.runtime.JobInstanceImpl;
import org.jberet.spi.PropertyKey;
import org.jberet.testapps.common.AbstractIT;

public abstract class PurgeRepositoryTestBase extends AbstractIT {
    protected static final long purgeSleepMillis = 2000;
    protected static final String prepurgeJobName = "prepurge";
    protected static final String prepurge2JobName = "prepurge2";
    protected static final String prepurgeAndPrepurge2JobNames = "prepurge, prepurge2";
    protected static final String chunkPartitionJobXml = "org.jberet.test.chunkPartition";

    public long prepurge(final String... jobName) throws Exception {
        final String prepurgeJobName = (jobName.length == 0) ? PurgeRepositoryTestBase.prepurgeJobName : jobName[0];
        startJob(prepurgeJobName);
        awaitTermination();
        assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
        System.out.printf("%s job execution id: %s, status: %s%n", prepurgeJobName, jobExecutionId, jobExecution.getBatchStatus());
        return jobExecutionId;
    }

    public void startAndVerifyPurgeJob(final String purgeJobXml) throws Exception {
        startJob(purgeJobXml);
        awaitTermination();

        //the current job will not be purged, and should complete
        assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
        assertNotNull(jobOperator.getJobExecution(jobExecutionId));
    }

    protected void noSuchJobException() throws Exception {
        final String[] noSuchJobNames = {"no-such-job-name", null, ""};
        for (final String noSuchJobName : noSuchJobNames) {
            try {
                final int result = jobOperator.getJobInstanceCount(noSuchJobName);
                fail("Expecting NoSuchJobException, but got " + result);
            } catch (final NoSuchJobException e) {
                System.out.printf("Got expected %s%n", e);
            }

            try {
                fail("Expecting NoSuchJobException, but got " + jobOperator.getJobInstances(noSuchJobName, 0, 1));
            } catch (final NoSuchJobException e) {
                System.out.printf("Got expected %s%n", e);
            }

            try {
                fail("Expecting NoSuchJobException, but got " + jobOperator.getRunningExecutions(noSuchJobName));
            } catch (final NoSuchJobException e) {
                System.out.printf("Got expected %s%n", e);
            }
        }
    }

    protected void noSuchJobInstanceException() throws Exception {
        JobInstanceImpl invalidJobInstance = new JobInstanceImpl(null, null, "xxxxxxxxxxxxxxx");
        try {
            final List<JobExecution> result = jobOperator.getJobExecutions(invalidJobInstance);
            if (result.isEmpty()) {
                System.out.printf("Got expected result: %s%n", result);
            } else {
                fail("Expecting NoSuchJobInstanceException, but got " + result);
            }
        } catch (final NoSuchJobInstanceException e) {
            System.out.printf("Got expected %s%n", e);
        }
    }

    /**
     * Starts and wait for the job to finish, and then call getRunningExecutions(jobName), which should return
     * empty List<Long>, since no job with jobName is running.
     *
     * @throws Exception
     */
    protected void getRunningExecutions() throws Exception {
        prepurge();
        final List<Long> runningExecutions = jobOperator.getRunningExecutions(prepurgeJobName);
        assertEquals(0, runningExecutions.size());
    }

    /**
     * Starts a job without waiting for it to finish, and then call getRunningExecutions(jobName), which should return
     * 1-element List<Long>. The job execution launches javascript engine (the batchlet is inline javascript) and so
     * should still be running when the test calls getRunningExecutions.
     *
     * @throws Exception
     */
    protected void getRunningExecutions2() throws Exception {
        startJob(prepurgeJobName);
        final List<Long> runningExecutions = jobOperator.getRunningExecutions(prepurgeJobName);
        assertEquals(1, runningExecutions.size());
        awaitTermination();
    }

    protected void getJobExecutionsByJob() throws Exception {
        final int loopCount = 3;
        for (int i = 0; i < loopCount; i++) {
            startJob(prepurgeJobName);
            startJob(prepurge2JobName);
        }

        // get job executions for job name prepurge, no limit
        List<JobExecution> executions = jobOperator.getJobExecutionsByJob(prepurgeJobName, Integer.MAX_VALUE);
        verifyJobExecutionsByJob(executions, 3, prepurgeJobName);

        // get job executions for job name prepurge, limit 2
        executions = jobOperator.getJobExecutionsByJob(prepurgeJobName, 2);
        verifyJobExecutionsByJob(executions, 2, prepurgeJobName);

        // get job executions for job name prepurge2, no limit
        executions = jobOperator.getJobExecutionsByJob(prepurge2JobName, Integer.MAX_VALUE);
        verifyJobExecutionsByJob(executions, 3, prepurge2JobName);

        // get job executions for job name prepurge2, limit 1
        executions = jobOperator.getJobExecutionsByJob(prepurge2JobName, 1);
        verifyJobExecutionsByJob(executions, 1, prepurge2JobName);

        // get all job executions
        executions = jobOperator.getJobExecutions(null);
        assertTrue(executions.size() >= loopCount * 2);
    }

    protected void memoryTest() throws Exception {
        final int times = Integer.getInteger("times", 5000);
        for (int i = 0; i < times; i++) {
            System.out.printf("================ %s ================ %n", i);

            params = new Properties();

            //add more job parameters to consume memory
            final String val = System.getProperty("user.dir");
            for (int n = 0; n < 20; n++) {
                params.setProperty(String.valueOf(n), val);
            }

            params.setProperty("thread.count", "10");
            params.setProperty("skip.thread.check", "true");
            params.setProperty("writer.sleep.time", "0");
            startJobAndWait(chunkPartitionJobXml);
            assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
        }
    }

    protected void ctrlC() throws Exception {
        params.setProperty("thread.count", "2");
        params.setProperty("skip.thread.check", "true");
        params.setProperty("writer.sleep.time", "3000");
        startJobAndWait(chunkPartitionJobXml);
    }

    protected void invalidRestartMode() throws Exception {
        final Properties restartParams = new Properties();
        restartParams.setProperty(PropertyKey.RESTART_MODE, "auto");
        restartKilled(restartParams);
    }

    protected void restartKilledStrict() throws Exception {
        final Properties restartParams = new Properties();
        restartParams.setProperty(PropertyKey.RESTART_MODE, PropertyKey.RESTART_MODE_STRICT);
        restartKilled(restartParams);
    }

    protected void restartKilled() throws Exception {
        restartKilled(null);
        assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
    }

    protected void restartKilledStopAbandon() throws Exception {
        final long originalJobExecutionId = getOriginalJobExecutionId(chunkPartitionJobXml);
        params.setProperty("writer.sleep.time", "0");

        final long restartExecutionId = jobOperator.restart(originalJobExecutionId, null);
        final JobExecutionImpl restartExecution = (JobExecutionImpl) jobOperator.getJobExecution(restartExecutionId);
        jobOperator.stop(restartExecutionId);
        restartExecution.awaitTermination(5, TimeUnit.MINUTES);
        jobOperator.abandon(restartExecutionId);
        jobOperator.abandon(originalJobExecutionId);
        assertEquals(BatchStatus.ABANDONED, jobOperator.getJobExecution(originalJobExecutionId).getBatchStatus());
        assertEquals(BatchStatus.ABANDONED, restartExecution.getBatchStatus());
    }

    protected void restartKilledForce() throws Exception {
        final Properties restartParams = new Properties();
        restartParams.setProperty(PropertyKey.RESTART_MODE, PropertyKey.RESTART_MODE_FORCE);
        restartKilled(restartParams);
        assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
    }

    protected void restartKilledDetect() throws Exception {
        final Properties restartParams = new Properties();
        restartParams.setProperty(PropertyKey.RESTART_MODE, PropertyKey.RESTART_MODE_DETECT);
        restartKilled(restartParams);
        assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
    }

    private void restartKilled(final Properties restartParams) throws InterruptedException {
        final long originalJobExecutionId = getOriginalJobExecutionId(chunkPartitionJobXml);
        params.setProperty("writer.sleep.time", "0");
        if (restartParams != null) {
            params.putAll(restartParams);
        }
        restartAndWait(originalJobExecutionId);
    }

    /**
     * Verifies job executions obtained by job name: expected number,
     * job execution id in descending order, and expected job name.
     * @param executions list of job exections by job name
     * @param expectedSize expected number of job exections
     * @param jobName expected job name in each job exection
     */
    private void verifyJobExecutionsByJob(List<JobExecution> executions, int expectedSize, String jobName) {
        System.out.printf("## executions for %s: %s%n", jobName,
                executions.stream().map(JobExecution::getExecutionId).collect(Collectors.toList()));
        assertEquals(expectedSize, executions.size());
        long previousExecutionId = Long.MAX_VALUE;
        for (JobExecution execution : executions) {
            assertTrue(execution.getExecutionId() < previousExecutionId);
            assertEquals(jobName, execution.getJobName());
            previousExecutionId = execution.getExecutionId();
        }
    }

    public static final class JobExecutionSelector1 implements JobExecutionSelector {
        private JobContext jobContext;
        private StepContext stepContext;

        @Override
        public boolean select(final JobExecution jobExecution,
                              final Collection<Long> allJobExecutionIds) {
            //select completed job executions and whose job name starts with "pre"
            if (jobExecution.getBatchStatus() == BatchStatus.COMPLETED && jobExecution.getJobName().startsWith("pre")) {
                System.out.printf("In select method of %s, return true.%n", this);
                return true;
            }
            System.out.printf("In select method of %s, return false.%n", this);
            return false;
        }

        @Override
        public JobContext getJobContext() {
            return jobContext;
        }

        @Override
        public void setJobContext(final JobContext jobContext) {
            this.jobContext = jobContext;
        }

        @Override
        public StepContext getStepContext() {
            return stepContext;
        }

        @Override
        public void setStepContext(final StepContext stepContext) {
            this.stepContext = stepContext;
        }
    }
}
