/*
 * Copyright (c) 2013-2015 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.jberet.runtime.context;

import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.batch.runtime.BatchStatus;
import jakarta.batch.runtime.Metric;
import jakarta.batch.runtime.context.StepContext;
import jakarta.enterprise.context.spi.Contextual;

import org.jberet._private.BatchLogger;
import org.jberet.creation.JobScopedContextImpl;
import org.jberet.job.model.JobFactory;
import org.jberet.job.model.Step;
import org.jberet.runtime.AbstractStepExecution;
import org.jberet.runtime.JobExecutionImpl;
import org.jberet.runtime.PartitionExecutionImpl;
import org.jberet.runtime.StepExecutionImpl;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Represents the execution context for either a step execution or partition execution.
 */
public class StepContextImpl extends AbstractContext implements StepContext, Cloneable {
    private Step step;

    /**
     * The step execution or partition execution associated with this context
     */
    private AbstractStepExecution stepExecution;

    private StepExecutionImpl originalStepExecution;
    private Boolean allowStartIfComplete;

    /**
     * A store for keeping CDI beans with {@link org.jberet.cdi.PartitionScoped} custom scopes.
     * Cleared at the end of the execution of the respecitve job, step, or partition, and all
     * stored beans are destroyed by calling
     * {@link JobScopedContextImpl.ScopedInstance#destroy(ConcurrentMap)}
     */
    private ConcurrentMap<Contextual<?>, JobScopedContextImpl.ScopedInstance<?>> partitionScopedBeans;

    public StepContextImpl(final Step step, final AbstractContext[] outerContexts) {
        super(step.getId(), outerContexts);
        this.step = step;
        this.classLoader = getJobContext().getClassLoader();
        this.stepExecution = getJobContext().jobRepository.createStepExecution(id);

        final JobExecutionImpl originalToRestart = getJobContext().originalToRestart;
        if (originalToRestart != null) {  //currently in a restarted execution
            originalStepExecution = getJobContext().getJobRepository().findOriginalStepExecutionForRestart(id, originalToRestart, classLoader);
            if (originalStepExecution != null) {
                if (originalStepExecution.getBatchStatus() == BatchStatus.COMPLETED) {
                    allowStartIfComplete = Boolean.valueOf(step.getAllowStartIfComplete());
                    if (allowStartIfComplete == Boolean.FALSE) {
                        stepExecution = originalStepExecution;
                        return;
                    }
                }
                if (originalStepExecution.getPersistentUserData() != null) {
                    this.stepExecution.setPersistentUserData(originalStepExecution.getPersistentUserData());
                }
                this.stepExecution.setReaderCheckpointInfo(originalStepExecution.getReaderCheckpointInfo());
                this.stepExecution.setWriterCheckpointInfo(originalStepExecution.getWriterCheckpointInfo());
                //partition execution data from previous step execution will be carried over later in step runner, if needed.
            } else {
                BatchLogger.LOGGER.couldNotFindOriginalStepToRestart(stepExecution.getStepExecutionId(), getStepName());
            }
        }

        this.stepExecution.setBatchStatus(BatchStatus.STARTING);
    }

    public StepContextImpl(final Step step,
                           final AbstractStepExecution stepExecution,
                           final AbstractContext[] outerContexts) {
        super(step.getId(), outerContexts);
        this.step = step;
        this.classLoader = getJobContext().getClassLoader();
        this.stepExecution = stepExecution;
        this.partitionScopedBeans = new ConcurrentHashMap<Contextual<?>, JobScopedContextImpl.ScopedInstance<?>>();
    }

    @Override
    public StepContextImpl clone() {
        StepContextImpl c = null;
        try {
            c = (StepContextImpl) super.clone();
            c.stepExecution = new PartitionExecutionImpl(stepExecution);
            c.outerContexts = new AbstractContext[outerContexts.length];
            c.outerContexts[0] = getJobContext().clone();
            for (int i = 1; i < c.outerContexts.length; i++) {
                c.outerContexts[i] = outerContexts[i];
            }
            if (WildFlySecurityManager.isChecking()) {
                c.step = AccessController.doPrivileged(new PrivilegedAction<Step>() {
                    @Override
                    public Step run() {
                        return JobFactory.cloneStep(step);
                    }
                });
            } else {
                c.step = JobFactory.cloneStep(step);
            }
            c.partitionScopedBeans = new ConcurrentHashMap<Contextual<?>, JobScopedContextImpl.ScopedInstance<?>>();
        } catch (CloneNotSupportedException e) {
            BatchLogger.LOGGER.failToClone(e, this, getJobContext().getJobName(), getStepName());
        }
        return c;
    }

    public Step getStep() {
        return this.step;
    }

    public AbstractStepExecution getStepExecution() {
        return this.stepExecution;
    }

    public Boolean getAllowStartIfComplete() {
        return allowStartIfComplete;
    }

    @Override
    public String getStepName() {
        return step.getId();
    }

    @Override
    public BatchStatus getBatchStatus() {
        return stepExecution.getBatchStatus();
    }

    @Override
    public void setBatchStatus(final BatchStatus status) {
        stepExecution.setBatchStatus(status);
    }

    @Override
    public String getExitStatus() {
        return stepExecution.getExitStatus();
    }

    @Override
    public void setExitStatus(final String exitStatus) {
        stepExecution.setExitStatus(exitStatus);
    }

    @Override
    public long getStepExecutionId() {
        return stepExecution.getStepExecutionId();
    }

    @Override
    public Properties getProperties() {
        return org.jberet.job.model.Properties.toJavaUtilProperties(step.getProperties());
    }

    @Override
    public Serializable getPersistentUserData() {
        return stepExecution.getPersistentUserData();
    }

    @Override
    public void setPersistentUserData(final Serializable data) {
        stepExecution.setPersistentUserData(data);
    }

    @Override
    public Exception getException() {
        return stepExecution.getException();
    }

    public void setException(final Exception e) {
        stepExecution.setException(e);
    }

    @Override
    public Metric[] getMetrics() {
        return stepExecution.getMetrics();
    }

    /**
     * Saves the execution data to job repository.
     * @param always if true, always saves data to job repository; if false,
     *               skip saving the step or partition if its batch status is {@code STOPPING}
     * @return 1 if the execution data is saved to job repository successfully; 0 otherwise
     */
    public int savePersistentData(boolean always) {
        if (always) {
            getJobContext().jobRepository.savePersistentData(getJobContext().jobExecution, stepExecution);
            return 1;
        } else {
            return getJobContext().jobRepository.savePersistentDataIfNotStopping(getJobContext().jobExecution, stepExecution);
        }
    }

    public StepExecutionImpl getOriginalStepExecution() {
        return originalStepExecution;
    }

    public ConcurrentMap<Contextual<?>, JobScopedContextImpl.ScopedInstance<?>> getPartitionScopedBeans() {
        return partitionScopedBeans;
    }
}
