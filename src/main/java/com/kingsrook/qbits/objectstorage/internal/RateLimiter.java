/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.objectstorage.internal;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/*******************************************************************************
 ** A per-connection token bucket (operations per second) plus a concurrency cap.
 ** One per app instance; multi-replica apps get per-replica limits.
 *******************************************************************************/
public class RateLimiter
{
   private final double    opsPerSecond;
   private final Semaphore concurrency;

   private double tokens;
   private long   lastRefillNanos;
   private long   throttledWaits = 0;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public RateLimiter(int opsPerSecond, int maxConcurrency)
   {
      this.opsPerSecond = Math.max(1, opsPerSecond);
      this.concurrency = new Semaphore(Math.max(1, maxConcurrency));
      this.tokens = this.opsPerSecond;
      this.lastRefillNanos = System.nanoTime();
   }



   /*******************************************************************************
    ** Wait for a token and a concurrency slot. Close the permit when the call ends.
    *******************************************************************************/
   public Permit acquire()
   {
      return (acquire(null));
   }



   /*******************************************************************************
    ** Wait no later than a caller's deadline, releasing the slot on interruption.
    *******************************************************************************/
   public Permit acquire(Instant deadline)
   {
      boolean acquired = false;
      try
      {
         if(deadline == null)
         {
            concurrency.acquire();
            acquired = true;
         }
         else
         {
            acquired = concurrency.tryAcquire(Math.max(0, Duration.between(Instant.now(), deadline).toNanos()), TimeUnit.NANOSECONDS);
            if(!acquired)
            {
               throw (new IllegalStateException("Storage probe deadline exceeded"));
            }
         }
         waitForToken(deadline);
         return (new Permit(concurrency));
      }
      catch(InterruptedException e)
      {
         if(acquired)
         {
            concurrency.release();
         }
         Thread.currentThread().interrupt();
         throw (new IllegalStateException("Interrupted while waiting for the storage rate limiter", e));
      }
      catch(RuntimeException e)
      {
         if(acquired)
         {
            concurrency.release();
         }
         throw (e);
      }
   }



   /*******************************************************************************
    ** Block until a token is available.
    *******************************************************************************/
   private void waitForToken(Instant deadline) throws InterruptedException
   {
      while(true)
      {
         if(deadline != null && !Instant.now().isBefore(deadline))
         {
            throw (new IllegalStateException("Storage probe deadline exceeded"));
         }
         long sleepMillis;
         synchronized(this)
         {
            long now = System.nanoTime();
            tokens = Math.min(opsPerSecond, tokens + (now - lastRefillNanos) / 1_000_000_000.0 * opsPerSecond);
            lastRefillNanos = now;
            if(tokens >= 1.0)
            {
               tokens -= 1.0;
               return;
            }
            throttledWaits++;
            sleepMillis = Math.max(1, (long) ((1.0 - tokens) / opsPerSecond * 1000));
         }
         Thread.sleep(deadline == null ? sleepMillis : Math.min(sleepMillis, Math.max(1, Duration.between(Instant.now(), deadline).toMillis())));
      }
   }



   /*******************************************************************************
    ** How many times a caller had to wait for a token.
    *******************************************************************************/
   public synchronized long getThrottledWaits()
   {
      return (throttledWaits);
   }



   /*******************************************************************************
    ** Releases the concurrency slot.
    *******************************************************************************/
   public static class Permit implements AutoCloseable
   {
      private final Semaphore semaphore;
      private boolean         closed = false;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      Permit(Semaphore semaphore)
      {
         this.semaphore = semaphore;
      }



      /*******************************************************************************
       ** Release.
       *******************************************************************************/
      @Override
      public void close()
      {
         if(!closed)
         {
            closed = true;
            semaphore.release();
         }
      }
   }
}
