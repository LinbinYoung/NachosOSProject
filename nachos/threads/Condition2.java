package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */

public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */

	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		this.waitqueue = new LinkedList<>();
		this.alarm = new Alarm();
	}
	
	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */

	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();
		conditionLock.release();
		this.waitqueue.add(KThread.currentThread());
		KThread.sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */

	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();
		if (!this.waitqueue.isEmpty()) {
			KThread stp = this.waitqueue.removeFirst();
			stp.ready();
			alarm.cancel(stp);
		}
		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */

	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();
		while (!this.waitqueue.isEmpty()) {
			this.wake();
		}
		Machine.interrupt().restore(status);
	}

     /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	
    public void sleepFor(long timeout) {
    	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    	boolean status = Machine.interrupt().disable();
    	conditionLock.release();
    	this.waitqueue.add(KThread.currentThread());
    	alarm.waitUntil(timeout);
    	System.out.println("Outside Thread" + ":" + alarm.cancel(KThread.currentThread()));
    	System.out.println("Outside Thread" + ":" + this.waitqueue.contains(KThread.currentThread()));
    	this.waitqueue.remove(KThread.currentThread());
    	conditionLock.acquire();
		Machine.interrupt().restore(status);
	}

    private Lock conditionLock;
    private LinkedList<KThread> waitqueue;
    private Alarm alarm;
    
    // Place Condition2 testing code in the Condition2 class.

    // Example of the "interlock" pattern where two threads strictly
    // alternate their execution with each other using a condition
    // variable.  (Also see the slide showing this pattern at the end
    // of Lecture 6.)

    private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run(){
                lock.acquire();
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   
                    cv.sleep();  // wait and release the condition lock, then acquire lock
                }
                lock.release();
            }
        }

        public InterlockTest(){
            lock = new Lock();
            cv = new Condition2(lock);
            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");
            ping.fork();
            pong.fork();
            ping.join();
            // for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }
    }
    
    private static class sleepForTest2{
    	private static Lock lock;
        private static Condition2 cv;
        
        private static class Interlocker implements Runnable {
            public void run(){
                lock.acquire();
                cv.sleepFor(100);
                System.out.println("Inside Thread" + ":" + cv.alarm.cancel(KThread.currentThread()));
                System.out.println("Inside Thread" + ":" + cv.waitqueue.contains(KThread.currentThread()));
                lock.release();
            }
        }
        public sleepForTest2() {
        	lock = new Lock();
        	cv = new Condition2(lock);
        	KThread testTh = new KThread(new Interlocker());
        	testTh.fork();
        	testTh.join();
        }
    }
    
    private static void sleepForTest1 () {
    	Lock lock = new Lock();
    	Condition2 cv = new Condition2(lock);
    	lock.acquire();
    	long t0 = Machine.timer().getTime();
    	System.out.println (KThread.currentThread().getName() + " sleeping");
    	// no other thread will wake us up, so we should time out
    	cv.sleepFor(2000);
    	long t1 = Machine.timer().getTime();
    	System.out.println (KThread.currentThread().getName() + " woke up, slept for " + (t1 - t0) + " ticks");
    	lock.release();
    }

    public static void selfTest1() {
    	new InterlockTest();
    	//sleepForTest1();
    	new sleepForTest2();
    }
    
}
