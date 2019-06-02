package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	
	public Alarm() {
		//Initialized the t_queue in the constructor
		this.t_queue = new PriorityQueue<>(new Comparator<Thread_with_time>() {
			@Override
			public int compare(Thread_with_time a1, Thread_with_time a2) {
				if (a1.waittime > a2.waittime) return 1;
				else if (a1.waittime < a2.waittime) return -1;
				else return 0;
			}
		});
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt(){
		long cur_systime = Machine.timer().getTime();
		boolean intStatus = Machine.interrupt().disable();
		while (!this.t_queue.isEmpty() && this.t_queue.peek().waittime <= cur_systime){
			this.t_queue.poll().thread.ready();
		}
		Machine.interrupt().restore(intStatus);
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */

	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long waketime = Machine.timer().getTime() + x;
		boolean intStatus = Machine.interrupt().disable();
		if (x <= 0) {
			Machine.interrupt().restore(intStatus);
			return;
		}
		this.t_queue.add(new Thread_with_time(KThread.currentThread(), waketime));
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}
    /**
     * Add self Test to the alarm class 
    */
    public static void alarmTest1(){
    	int durations[] = {10*1000, 1000, 100*1000};
    	long t0, t1;
    	for (int d : durations){
    		t0 = Machine.timer().getTime();
    		ThreadedKernel.alarm.waitUntil (d);
    		t1 = Machine.timer().getTime();
    		System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
    	}
    	System.out.println("\nMy own Test case for Q1 and Q2");
    	System.out.println("test1 \ntest2 \ntest3(join) \n");
    	KThread test1 = new KThread( new Runnable () {
    		public void run() {
    			ThreadedKernel.alarm.waitUntil(100);
    		    System.out.println ("I am test1 sleep 100 ticks");
    		}
    	    });
    	test1.setName("test1");
    	
    	KThread test2 = new KThread( new Runnable () {
    		public void run() {
    			ThreadedKernel.alarm.waitUntil(30);
    		    System.out.println ("I am test2 sleep 30 ticks");
    		}
    	    });
    	test2.setName("test2");
    	
    	KThread test3 = new KThread( new Runnable () {
    		public void run() {
    			ThreadedKernel.alarm.waitUntil(10);
    		    System.out.println ("I am test3 sleep 10 ticks");
    		}
    	    });
    	test3.setName("test3");
    	test1.fork();
    	test2.fork();
    	test3.fork();
    	test3.join();
    	System.out.println("");
    }
     /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
     public boolean cancel(KThread threadtest) {
    	 boolean intStatus = Machine.interrupt().disable();
    	 for (Thread_with_time elem : this.t_queue) {
    		 if (elem.thread == threadtest) {
    			 this.t_queue.remove(elem);
    			 Machine.interrupt().restore(intStatus);
    			 return true;
    		 }
    	 }
    	 Machine.interrupt().restore(intStatus);
    	 return false;
	 }
 	/**
 	 * Initialized data structure here
 	 */
     
     class Thread_with_time{
    	 KThread thread;
    	 long waittime;
    	 Thread_with_time(KThread thread, long waittime){
    		 this.thread = thread;
    		 this.waittime = waittime;
    	 }
     }
     
     private PriorityQueue<Thread_with_time> t_queue;
}
