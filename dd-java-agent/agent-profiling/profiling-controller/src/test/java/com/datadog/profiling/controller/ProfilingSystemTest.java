/*
 * Copyright 2019 Datadog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadog.profiling.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for testing the {@link com.datadog.profiling.controller.ProfilingSystem}.
 *
 * <p>TODO: review these tests to make sure they are actually reasonable for any {@link Controller}
 * implementation
 */
public class ProfilingSystemTest {

  private final Controller controller = new TestController();

  @Test
  public void testCanShutDownWithoutStarting()
      throws UnsupportedEnvironmentException, ConfigurationException {
    final RecordingDataListener listener = (final RecordingData data) -> {};
    final ProfilingSystem system =
        new ProfilingSystem(
            controller, listener, Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(100));
    system.shutdown();
  }

  @Test
  public void testDoesntSendDataIfNotStarted()
      throws UnsupportedEnvironmentException, IOException, InterruptedException,
          ConfigurationException {
    final CountDownLatch latch = new CountDownLatch(1);
    final RecordingDataListener listener =
        (final RecordingData data) -> {
          latch.countDown();
        };
    final ProfilingSystem system =
        new ProfilingSystem(
            controller, listener, Duration.ZERO, Duration.ofMillis(1), Duration.ofMillis(1));
    latch.await(10, TimeUnit.MILLISECONDS);
    system.shutdown();
    assertEquals(
        "Got recording data even though the system was never started!", 1, latch.getCount());
  }

  /**
   * Ensuring it is not possible to configure the profiling system to have greater recording lengths
   * than the dump periodicity.
   */
  @Test
  public void testProfilingSystemCreationBadConfig() {
    final RecordingDataListener listener =
        (final RecordingData data) -> {
          // Don't care...
        };
    try {
      final ProfilingSystem system =
          new ProfilingSystem(
              controller, listener, Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(400));
      system.shutdown();
    } catch (final Exception e) {
      return;
    }

    Assert.fail("An exception should have been thrown at this point!");
  }

  /**
   * Ensuring that it can be started, and recording data for a few profiling recordings captured.
   */
  @Test
  public void testProfilingSystem()
      throws UnsupportedEnvironmentException, IOException, InterruptedException,
          ConfigurationException {
    final CountDownLatch latch = new CountDownLatch(2);
    final List<RecordingData> results = new ArrayList<>();

    final RecordingDataListener listener =
        (final RecordingData data) -> {
          results.add(data);
          latch.countDown();
        };
    final ProfilingSystem system =
        new ProfilingSystem(
            controller, listener, Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(25));
    system.start();
    latch.await(30, TimeUnit.SECONDS);
    assertTrue("Should have received more data!", results.size() >= 2);
    for (final RecordingData data : results) {
      assertTrue("RecordingData should be available before sent out!", data.isAvailable());
    }
    system.shutdown();
    for (final RecordingData data : results) {
      data.release();
    }
  }

  /** Ensuring that it can be started, and recording data for the continuous recording captured. */
  @Test
  public void testContinuous()
      throws UnsupportedEnvironmentException, IOException, InterruptedException,
          ConfigurationException {
    final CountDownLatch latch = new CountDownLatch(2);
    final List<RecordingData> results = new ArrayList<>();

    final RecordingDataListener listener =
        (final RecordingData data) -> {
          results.add(data);
          latch.countDown();
        };
    final ProfilingSystem system =
        new ProfilingSystem(
            controller, listener, Duration.ofDays(1), Duration.ofDays(1), Duration.ofSeconds(2));
    system.start();
    final Runnable continuousTrigger =
        () -> {
          for (int i = 0; i < 3; i++) {
            try {
              system.triggerSnapshot();
              try {
                Thread.sleep(200);
              } catch (final InterruptedException e) {
              }
            } catch (final IOException e) {
              // TODO: do not do this, fail test instead maybe?
              e.printStackTrace();
            }
          }
        };
    new Thread(continuousTrigger, "Continuous trigger").start();
    latch.await(30, TimeUnit.SECONDS);
    assertTrue("Should have received more data!", results.size() >= 2);
    for (final RecordingData data : results) {
      assertEquals("Getting snapshots", "snapshot", data.getName());
      assertTrue("RecordingData should be available before sent out!", data.isAvailable());
    }
    system.shutdown();
    for (final RecordingData data : results) {
      data.release();
    }
  }

  private static class TestController implements Controller {

    @Override
    public RecordingData createRecording(
        final String recordingName, final Map<String, String> template, final Duration duration)
        throws IOException {
      final RecordingData recordingData = mock(RecordingData.class);
      when(recordingData.isAvailable()).thenReturn(true);
      when(recordingData.getName()).thenReturn("single-recording");
      return recordingData;
    }

    @Override
    public RecordingData createContinuousRecording(
        final String recordingName, final Map<String, String> template) {
      final RecordingData recordingData = mock(RecordingData.class);
      when(recordingData.isAvailable()).thenReturn(true);
      when(recordingData.getName()).thenReturn("continuous-recording");
      return recordingData;
    }

    @Override
    public RecordingData snapshot() throws IOException {
      final RecordingData recordingData = mock(RecordingData.class);
      when(recordingData.isAvailable()).thenReturn(true);
      when(recordingData.getName()).thenReturn("snapshot");
      return recordingData;
    }

    @Override
    public Map<String, String> getContinuousSettings() throws IOException {
      return null;
    }

    @Override
    public Map<String, String> getProfilingSettings() throws IOException {
      return null;
    }
  }
}
