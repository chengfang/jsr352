/*
 * Copyright (c) 2013-2015 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.jberet.testapps.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.chunk.ItemWriter;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("integerArrayWriter")
public class IntegerArrayWriter extends IntegerArrayReaderWriterProcessorBase implements ItemWriter {
    @Inject
    @BatchProperty(name = "writer.sleep.time")
    protected long writerSleepTime;

    @Override
    public void writeItems(final List<Object> items) throws Exception {
        if (items == null) {
            return;
        }

        if (failOnValues != null) {
            Object matchingValue = null;
            for (final Object e : items) {
                if (Arrays.binarySearch(failOnValues, e) >= 0) {
                    matchingValue = e;
                    break;  //only find the first match
                }
            }
            if (matchingValue != null && (repeatFailure || !failedValues.contains(matchingValue))) {
                failedValues.add((Integer) matchingValue);
                System.out.printf("About to throw ArithmeticException on value %s%n", matchingValue);
                throw new ArithmeticException("integerArrayWriter failing on value " + matchingValue);
            }
        }

        if (writerSleepTime > 0) {
            Thread.sleep(writerSleepTime);
        }

        //print acts as writing by this ItemWriter
        System.out.printf("Wrote Chunk (%s Items): %s%n", items.size(), String.valueOf(items));

        //record items into stepContext
        ArrayList<List<Object>> recorded = (ArrayList<List<Object>>) stepContext.getPersistentUserData();
        if (recorded == null) {
            recorded = new ArrayList<List<Object>>();
        }
        recorded.add(items);
        // User data is immutable and needs to be set after the previous data has been mutated
        stepContext.setPersistentUserData(recorded);
    }

    @Override
    public void open(final Serializable checkpoint) throws Exception {
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        return null;
    }
}
