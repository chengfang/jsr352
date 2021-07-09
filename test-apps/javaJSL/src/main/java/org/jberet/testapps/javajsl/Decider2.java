/*
 * Copyright (c) 2015 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
 
package org.jberet.testapps.javajsl;

import jakarta.batch.api.Decider;
import jakarta.batch.runtime.StepExecution;
import jakarta.inject.Named;

@Named
public class Decider2 implements Decider {
    @Override
    public String decide(final StepExecution[] executions) throws Exception {
        return "xxx";
    }
}
