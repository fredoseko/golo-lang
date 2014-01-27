/*
 * Copyright 2012-2014 Institut National des Sciences Appliquées de Lyon (INSA-Lyon)
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

package gololang.concurrent.async;

import org.testng.annotations.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class PromiseTest {

  @Test
  public void basics_set() {
    Promise p = new Promise();
    assertThat(p.isResolved(), is(false));
    assertThat(p.isFailed(), is(false));
    assertThat(p.get(), nullValue());
    p.set("Plop!");
    assertThat(p.isResolved(), is(true));
    assertThat(p.isFailed(), is(false));
    assertThat(p.get(), is((Object) "Plop!"));
    p.set("Hey!");
    assertThat(p.get(), is((Object) "Plop!"));
  }

  @Test
  public void basics_fail() {
    Promise p = new Promise();
    p.fail(new RuntimeException("w00t"));
    assertThat(p.isResolved(), is(true));
    assertThat(p.isFailed(), is(true));
    assertThat(p.get(), instanceOf(RuntimeException.class));
  }

  @Test(timeOut = 5000)
  public void basics_blocking() throws InterruptedException {
    final Promise p = new Promise();
    final CountDownLatch latch = new CountDownLatch(2);
    new Thread() {
      @Override
      public void run() {
        try {
          latch.countDown();
          latch.await();
          p.set("Yes!");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }.start();
    latch.countDown();
    Object result = p.blockingGet();
    assertThat(result, is((Object) "Yes!"));
  }

  @Test
  public void observe_monothread_set_after() {
    final Promise p = new Promise();
    final AtomicInteger i = new AtomicInteger(0);
    p.future().onSet(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.addAndGet((Integer) value);
      }
    }).onSet(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.addAndGet(100);
      }
    }).onFail(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.set(666);
      }
    });
    p.set(10);
    assertThat(p.future().isResolved(), is(true));
    assertThat(p.future().get(), is((Object) 10));
    assertThat(i.get(), is(110));
  }

  @Test
  public void observe_monothread_set_before() {
    final Promise p = new Promise();
    final AtomicInteger i = new AtomicInteger(0);
    p.set(10);
    p.future().onSet(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.addAndGet((Integer) value);
      }
    }).onSet(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.addAndGet(100);
      }
    }).onFail(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.set(666);
      }
    });
    assertThat(p.future().isResolved(), is(true));
    assertThat(p.future().get(), is((Object) 10));
    assertThat(i.get(), is(110));
  }

  @Test(timeOut = 5000)
  public void observe_threaded_set() {
    final Promise p = new Promise();
    final AtomicInteger i = new AtomicInteger(0);
    final Future future = p.future();
    final CountDownLatch latch = new CountDownLatch(1);
    future.onFail(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.addAndGet(100);
      }
    }).onSet(new Functions.Observer() {
      @Override
      public void apply(Object value) {
        i.addAndGet(666);
      }
    });
    new Thread() {
      @Override
      public void run() {
        p.fail(new RuntimeException("Plop"));
        latch.countDown();
      }
    }.start();
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThat(future.isFailed(), is(true));
    assertThat(i.get(), is(100));
    assertThat(future.get(), instanceOf(RuntimeException.class));
  }

  @Test
  public void map_future() {
    Promise p = new Promise();
    Future mapped = p.future().map(new Functions.Transformer() {
      @Override
      public Object apply(Object value) {
        return value + "!";
      }
    });
    p.set("Hey");
    assertThat(mapped.isResolved(), is(true));
    assertThat(mapped.get(), is((Object) "Hey!"));
  }

  @Test
  public void map_future_fail() {
    Promise p = new Promise();
    Future mapped = p.future().map(new Functions.Transformer() {
      @Override
      public Object apply(Object value) {
        return value + "!";
      }
    });
    p.fail(new IllegalArgumentException("Plop da plop"));
    assertThat(mapped.isResolved(), is(true));
    assertThat(mapped.isFailed(), is(true));
    assertThat(mapped.get(), instanceOf(IllegalArgumentException.class));
  }

  @Test
  public void filter_future() {
    Promise p = new Promise();
    Future filtered = p.future().filter(new Functions.Filter() {
      @Override
      public boolean apply(Object value) {
        return value instanceof String;
      }
    });
    p.set("Hey!");
    assertThat(filtered.isResolved(), is(true));
    assertThat(filtered.get(), is((Object) "Hey!"));
  }

  @Test
  public void filter_future_false() {
    Promise p = new Promise();
    Future filtered = p.future().filter(new Functions.Filter() {
      @Override
      public boolean apply(Object value) {
        return value instanceof String;
      }
    });
    p.set(1);
    assertThat(filtered.isResolved(), is(true));
    assertThat(filtered.isFailed(), is(true));
    assertThat(filtered.get(), instanceOf(NoSuchElementException.class));
  }

  @Test
  public void filter_future_fail() {
    Promise p = new Promise();
    Future filtered = p.future().filter(new Functions.Filter() {
      @Override
      public boolean apply(Object value) {
        return value instanceof String;
      }
    });
    p.fail(new IllegalArgumentException("Plop da plop"));
    assertThat(filtered.isResolved(), is(true));
    assertThat(filtered.isFailed(), is(true));
    assertThat(filtered.get(), instanceOf(IllegalArgumentException.class));
  }
}
